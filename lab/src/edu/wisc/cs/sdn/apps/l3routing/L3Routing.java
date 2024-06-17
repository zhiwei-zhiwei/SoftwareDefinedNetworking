package edu.wisc.cs.sdn.apps.l3routing;

import java.util.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.wisc.cs.sdn.apps.util.Host;
import edu.wisc.cs.sdn.apps.util.SwitchCommands;

import org.openflow.protocol.OFMatch;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.instruction.OFInstruction;
import org.openflow.protocol.instruction.OFInstructionApplyActions;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitch.PortChangeType;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceListener;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryListener;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.routing.Link;

public class L3Routing implements IFloodlightModule, IOFSwitchListener, 
		ILinkDiscoveryListener, IDeviceListener
{
	public static final String MODULE_NAME = L3Routing.class.getSimpleName();
	
	// Interface to the logging system
    private static Logger log = LoggerFactory.getLogger(MODULE_NAME);
    
    // Interface to Floodlight core for interacting with connected switches
    private IFloodlightProviderService floodlightProv;

    // Interface to link discovery service
    private ILinkDiscoveryService linkDiscProv;

    // Interface to device manager service
    private IDeviceService deviceProv;
    
    // Switch table in which rules should be installed
    public static byte table;
    
    // Map of hosts to devices
    private Map<IDevice,Host> knownHosts;

	

	/**
     * Loads dependencies and initializes data structures.
     */
	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException 
	{
		log.info(String.format("Initializing %s...", MODULE_NAME));
		Map<String,String> config = context.getConfigParams(this);
        table = Byte.parseByte(config.get("table"));
        
		this.floodlightProv = context.getServiceImpl(
				IFloodlightProviderService.class);
        this.linkDiscProv = context.getServiceImpl(ILinkDiscoveryService.class);
        this.deviceProv = context.getServiceImpl(IDeviceService.class);
        
        this.knownHosts = new ConcurrentHashMap<IDevice,Host>();
	}
	// reference: https://en.wikipedia.org/wiki/Bellman%E2%80%93Ford_algorithm
	private Map<Long, Integer> generateRouterTable(IOFSwitch iSwitch) 
	{
	    Map<Long, Integer> distance = new ConcurrentHashMap<Long, Integer>();
	    Map<Long, Integer> predecessor = new ConcurrentHashMap<Long,Integer>();
	    
		Queue<Long> tempQ = new LinkedList<Long>();
	    Collection<Link> tempL;

	    for (IOFSwitch sw : this.getSwitches().values()) 
	    {
			// for each vertex (IOFSwitch) sw in vertices do
	    	distance.put(sw.getId(), Integer.MAX_VALUE);
			// Initialize the distance to all vertices to infinity
	    }

	    distance.put(iSwitch.getId(), 0); // put into the map
		// The distance from the source to itself is, of course, zero

	    for (int i = 0; i < this.getSwitches().size(); i++) 
	    {
	    	// tempL = this.rmDupLink();
			Collection<Link> tempLi = new ArrayList<Link>();
			boolean tempCheck;
			for (Link li : this.getLinks()) {
				tempCheck = false;
				for (Link li2 : tempLi) {
					if(li.getSrc() == li2.getSrc() && li.getDst() == li2.getDst() || li.getSrc() == li2.getDst() && li.getDst() == li2.getSrc()){
						tempCheck = true;
						break;
					}
				}
				if(!tempCheck){
					tempLi.add(li);
				}
			}
			tempL = tempLi;

	    	tempQ.add(iSwitch.getId());

	    	while (!tempQ.isEmpty()){
	    		long curId = tempQ.remove();
				Collection<Link> cLinks = new ArrayList<Link>();
				Collection<Link> iLinks = new ArrayList<Link>();
				//  find the connected links of the map
				for(Link li : tempL){
					if (li.getSrc() == curId || li.getDst() == curId){
						iLinks.add(li);
					}
				}
				cLinks = iLinks;

				// reference to https://www.geeksforgeeks.org/bellman-ford-algorithm-dp-23/
	    		for (Link l : cLinks){
	    			int curDistance = distance.get(curId);
					int nextDistance = Integer.MAX_VALUE;
				    if (curId == l.getSrc()) {
				    	nextDistance = distance.get(l.getDst());
				    	if (nextDistance > (curDistance + 1)) {
				    		distance.put(l.getDst(), (curDistance + 1));
				    		predecessor.put(l.getDst(), l.getDstPort());
				    	}
				    	tempQ.add(l.getDst());
				    } 
				    else {
				    	nextDistance = distance.get(l.getSrc());
				    	if (nextDistance > (curDistance + 1)) {
				    		distance.put(l.getSrc(), (curDistance + 1));
				    		predecessor.put(l.getSrc(), l.getSrcPort());
				    	}
				    	tempQ.add(l.getSrc());
				    }
				    tempL.remove(l);
	    		}
	    	}
	    }
	    return predecessor;
	}

	// Bellman-Ford algorith
	// reference to https://www.geeksforgeeks.org/bellman-ford-algorithm-dp-23/
	private int[] bellman_ford(int a, int swNum, int linkNum, int srcNum, Link[] link, Host[] host, IOFSwitch[] iswitch){
		int[] dist = new int[swNum];
		for(int i = 0; i < swNum; i++){
			dist[i] = Integer.MAX_VALUE;
		}
		dist[srcNum] = 0;

		int[] temp = new int[swNum];
		int[] tempPort = new int[swNum];
		for(int i = 0; i < swNum; i++){
			for (int j = 0; j < linkNum; j++) {
				int u = 0;
				int v = 0;
				int weight = 1;
				for (int k = 0; k < swNum; k++) {
					if(link[j].getDst() == iswitch[k].getId()){
						u = k;
						break;
					}
				}
				for (int k = 0; k < swNum; k++) {
					if(link[j].getSrc() == iswitch[k].getId()){
						v = k;
						break;
					}
				}
				if(dist[u] != Integer.MAX_VALUE && dist[u] + weight < dist[v]){
					dist[v] = dist[u] + weight;
					temp[v] = u;
					tempPort[v] = link[j].getSrcPort();
				}
				if(dist[v] != Integer.MAX_VALUE && dist[v] + weight < dist[u]){
					dist[u] = dist[v] + weight;
					temp[u] = v;
					tempPort[u] = link[j].getDstPort();
				}
			}
		}
		tempPort[srcNum] = host[a].getPort();
		return tempPort;
	}

	private void generateTable(){
		Link[] link = this.getLinks().toArray(new Link[0]);
		Host[] host = this.getHosts().toArray(new Host[0]);
		IOFSwitch[] iswitch = this.getSwitches().values().toArray(new IOFSwitch[0]);
		// link.toArray(new Link[0]);
		// host.toArray(new Host[0]);
		// iswitch.values().toArray(new IOFSwitch[0]);

		int swNum = this.getSwitches().size(), hostNum = this.getHosts().size(), linkNum = this.getLinks().size();

		for (int i = 0; i < hostNum; i++) {
			IOFSwitch switchSrc = host[i].getSwitch();
			if(switchSrc == null) continue;
			int srcNum = 0;
			for (int j = 0; j < swNum; j++) {
				if(switchSrc.getId() == iswitch[j].getId()){
					srcNum = j;
					break;
				}
			}
			// int[] tempPort = bellman_ford(i, swNum, linkNum, srcNum, link, host, iswitch);
			
			/////
			int[] dist = new int[swNum];
			for(int j = 0; j < swNum; j++){
				dist[j] = Integer.MAX_VALUE;
			}
			dist[srcNum] = 0;

			int[] temp = new int[swNum];
			int[] tempPort = new int[swNum];
			for(int j = 0; j < swNum; j++){
				for (int k = 0; k < linkNum; k++) {
					int u = 0;
					int v = 0;
					int weight = 1;
					for (int m = 0; m < swNum; m++) {
						if(link[k].getDst() == iswitch[m].getId()){
							u = m;
							break;
						}
					}
					for (int m = 0; m < swNum; m++) {
						if(link[k].getSrc() == iswitch[m].getId()){
							v = m;
							break;
						}
					}
					if(dist[u] != Integer.MAX_VALUE && dist[u] + weight < dist[v]){
						dist[v] = dist[u] + weight;
						temp[v] = u;
						tempPort[v] = link[j].getSrcPort();
					}
					if(dist[v] != Integer.MAX_VALUE && dist[v] + weight < dist[u]){
						dist[u] = dist[v] + weight;
						temp[u] = v;
						tempPort[u] = link[j].getDstPort();
					}
				}
			}
			tempPort[srcNum] = host[i].getPort();


			for (int j = 0; j < swNum; j++) {
				OFMatch ofmatch = new OFMatch();
				ofmatch.setDataLayerType(OFMatch.ETH_TYPE_IPV4);
				ofmatch.setNetworkDestination(OFMatch.ETH_TYPE_IPV4, host[i].getIPv4Address());
				OFAction ofaction = new OFActionOutput(tempPort[j]);
				OFInstruction ofinstruction = new OFInstructionApplyActions(Arrays.asList(ofaction));
				SwitchCommands.installRule(iswitch[j], table, SwitchCommands.DEFAULT_PRIORITY, ofmatch, Arrays.asList(ofinstruction));
			}
		}

	}

	/**
     * Subscribes to events and performs other startup tasks.
     */
	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException 
	{
		log.info(String.format("Starting %s...", MODULE_NAME));
		this.floodlightProv.addOFSwitchListener(this);
		this.linkDiscProv.addListener(this);
		this.deviceProv.addListener(this);
		
		/*********************************************************************/
		/* TODO: Initialize variables or perform startup tasks, if necessary */
		
		/*********************************************************************/
	}
	
    /**
     * Get a list of all known hosts in the network.
     */
    private Collection<Host> getHosts()
    { return this.knownHosts.values(); }
	
    /**
     * Get a map of all active switches in the network. Switch DPID is used as
     * the key.
     */
	private Map<Long, IOFSwitch> getSwitches()
    { return floodlightProv.getAllSwitchMap(); }
	
    /**
     * Get a list of all active links in the network.
     */
    private Collection<Link> getLinks()
    { return linkDiscProv.getLinks().keySet(); }

    /**
     * Event handler called when a host joins the network.
     * @param device information about the host
     */
	@Override
	public void deviceAdded(IDevice device) 
	{
		Host host = new Host(device, this.floodlightProv);
		// We only care about a new host if we know its IP
		if (host.getIPv4Address() != null)
		{
			log.info(String.format("Host %s added", host.getName()));
			this.knownHosts.put(device, host);
			
			/*****************************************************************/
			/* TODO: Update routing: add rules to route to new host          */
			if(host.isAttachedToSwitch() == false) return;
			else{
				Map<Long, Integer> routes = generateRouterTable(host.getSwitch());
				OFMatch oMatch = new OFMatch();
				oMatch.setDataLayerType(OFMatch.ETH_TYPE_IPV4);
				oMatch.setNetworkDestination(host.getIPv4Address());
				for (Long id : routes.keySet()) 
				{
					OFAction oAction = new OFActionOutput(routes.get(id));
					OFInstruction oInstruction = new OFInstructionApplyActions(Arrays.asList(oAction));
					SwitchCommands.installRule(this.getSwitches().get(id), this.table,SwitchCommands.DEFAULT_PRIORITY, oMatch,Arrays.asList(oInstruction));
				}
				OFAction oAction = new OFActionOutput(host.getPort());
				OFInstruction oInstruction = new OFInstructionApplyActions(Arrays.asList(oAction));
				SwitchCommands.installRule(host.getSwitch(), this.table, SwitchCommands.DEFAULT_PRIORITY, oMatch, Arrays.asList(oInstruction));
			}
			/*****************************************************************/
		}
	}

	/**
     * Event handler called when a host is no longer attached to a switch.
     * @param device information about the host
     */
	@Override
	public void deviceRemoved(IDevice device) 
	{
		Host host = this.knownHosts.get(device);
		if (null == host)
		{ return; }
		this.knownHosts.remove(device);
		
		log.info(String.format("Host %s is no longer attached to a switch", 
				host.getName()));
		
		/*********************************************************************/
		/* TODO: Update routing: remove rules to route to host               */
		// int swNum = this.getSwitches().size();
		for (IOFSwitch iswitch : this.getSwitches().values()){
			OFMatch oMatch = new OFMatch();
			oMatch.setDataLayerType(OFMatch.ETH_TYPE_IPV4);
			oMatch.setNetworkDestination(host.getIPv4Address());
			SwitchCommands.removeRules(iswitch, this.table, oMatch);
		}
		/*********************************************************************/
	}

	/**
     * Event handler called when a host moves within the network.
     * @param device information about the host
     */
	@Override
	public void deviceMoved(IDevice device) 
	{
		Host host = this.knownHosts.get(device);
		if (null == host)
		{
			host = new Host(device, this.floodlightProv);
			this.knownHosts.put(device, host);
		}
		
		if (!host.isAttachedToSwitch())
		{
			this.deviceRemoved(device);
			return;
		}
		log.info(String.format("Host %s moved to s%d:%d", host.getName(),
				host.getSwitch().getId(), host.getPort()));
		
		/*********************************************************************/
		/* TODO: Update routing: change rules to route to host               */
		for (IOFSwitch iswitch : this.getSwitches().values()){
			OFMatch oMatch = new OFMatch();
			oMatch.setDataLayerType(OFMatch.ETH_TYPE_IPV4);
			oMatch.setNetworkDestination(host.getIPv4Address());
			SwitchCommands.removeRules(iswitch, this.table, oMatch);
		}
		if(host.isAttachedToSwitch() == false) {
			return;
		}
		else{
			Map<Long, Integer> routes = generateRouterTable(host.getSwitch());
			OFMatch oMatch = new OFMatch();
			oMatch.setDataLayerType(OFMatch.ETH_TYPE_IPV4);
			oMatch.setNetworkDestination(host.getIPv4Address());
			for (Long id : routes.keySet()) 
			{
				OFAction oAction = new OFActionOutput(routes.get(id));
				OFInstruction oInstruction = new OFInstructionApplyActions(Arrays.asList(oAction));
				SwitchCommands.installRule(this.getSwitches().get(id), this.table,SwitchCommands.DEFAULT_PRIORITY, oMatch,Arrays.asList(oInstruction));
			}
			OFAction oAction = new OFActionOutput(host.getPort());
			OFInstruction oInstruction = new OFInstructionApplyActions(Arrays.asList(oAction));
			SwitchCommands.installRule(host.getSwitch(), this.table, SwitchCommands.DEFAULT_PRIORITY, oMatch, Arrays.asList(oInstruction));
		}
		/*********************************************************************/
	}
	
    /**
     * Event handler called when a switch joins the network.
     * @param DPID for the switch
     */
	@Override		
	public void switchAdded(long switchId) 
	{
		IOFSwitch sw = this.floodlightProv.getSwitch(switchId);
		log.info(String.format("Switch s%d added", switchId));
		
		/*********************************************************************/
		/* TODO: Update routing: change routing rules for all hosts          */
		for(Host host : this.getHosts()){
			for (IOFSwitch iswitch : this.getSwitches().values()){
				OFMatch match = new OFMatch();
				match.setDataLayerType(OFMatch.ETH_TYPE_IPV4);
				match.setNetworkDestination(host.getIPv4Address());
				SwitchCommands.removeRules(iswitch, this.table, match);
			}
			if(host.isAttachedToSwitch() == false) {
				return;
			}
			else{
				Map<Long, Integer> routes = generateRouterTable(host.getSwitch());
				OFMatch oMatch = new OFMatch();
				oMatch.setDataLayerType(OFMatch.ETH_TYPE_IPV4);
				oMatch.setNetworkDestination(host.getIPv4Address());
				for (Long id : routes.keySet()) 
				{
					OFAction oAction = new OFActionOutput(routes.get(id));
					OFInstruction oInstruction = new OFInstructionApplyActions(Arrays.asList(oAction));
					SwitchCommands.installRule(this.getSwitches().get(id), this.table,SwitchCommands.DEFAULT_PRIORITY, oMatch,Arrays.asList(oInstruction));
				}
				OFAction oAction = new OFActionOutput(host.getPort());
				OFInstruction oInstruction = new OFInstructionApplyActions(Arrays.asList(oAction));
				SwitchCommands.installRule(host.getSwitch(), this.table, SwitchCommands.DEFAULT_PRIORITY, oMatch, Arrays.asList(oInstruction));
			}
		}
		/*********************************************************************/
	}

	/**
	 * Event handler called when a switch leaves the network.
	 * @param DPID for the switch
	 */
	@Override
	public void switchRemoved(long switchId) 
	{
		IOFSwitch sw = this.floodlightProv.getSwitch(switchId);
		log.info(String.format("Switch s%d removed", switchId));
		
		/*********************************************************************/
		/* TODO: Update routing: change routing rules for all hosts          */
		for(Host host : this.getHosts()){
			for (IOFSwitch iswitch : this.getSwitches().values()){
				OFMatch match = new OFMatch();
				match.setDataLayerType(OFMatch.ETH_TYPE_IPV4);
				match.setNetworkDestination(host.getIPv4Address());
				SwitchCommands.removeRules(iswitch, this.table, match);
			}
			if(host.isAttachedToSwitch() == false) {
				return;
			}
			else{
				Map<Long, Integer> routes = generateRouterTable(host.getSwitch());
				OFMatch oMatch = new OFMatch();
				oMatch.setDataLayerType(OFMatch.ETH_TYPE_IPV4);
				oMatch.setNetworkDestination(host.getIPv4Address());
				for (Long id : routes.keySet()) 
				{
					OFAction oAction = new OFActionOutput(routes.get(id));
					OFInstruction oInstruction = new OFInstructionApplyActions(Arrays.asList(oAction));
					SwitchCommands.installRule(this.getSwitches().get(id), this.table,SwitchCommands.DEFAULT_PRIORITY, oMatch,Arrays.asList(oInstruction));
				}
				OFAction oAction = new OFActionOutput(host.getPort());
				OFInstruction oInstruction = new OFInstructionApplyActions(Arrays.asList(oAction));
				SwitchCommands.installRule(host.getSwitch(), this.table, SwitchCommands.DEFAULT_PRIORITY, oMatch, Arrays.asList(oInstruction));
			}
		}
		/*********************************************************************/
	}

	/**
	 * Event handler called when multiple links go up or down.
	 * @param updateList information about the change in each link's state
	 */
	@Override
	public void linkDiscoveryUpdate(List<LDUpdate> updateList) 
	{
		for (LDUpdate update : updateList)
		{
			// If we only know the switch & port for one end of the link, then
			// the link must be from a switch to a host
			if (0 == update.getDst())
			{
				log.info(String.format("Link s%s:%d -> host updated", 
					update.getSrc(), update.getSrcPort()));
			}
			// Otherwise, the link is between two switches
			else
			{
				log.info(String.format("Link s%s:%d -> s%s:%d updated", 
					update.getSrc(), update.getSrcPort(),
					update.getDst(), update.getDstPort()));
			}
		}
		
		/*********************************************************************/
		/* TODO: Update routing: change routing rules for all hosts          */
		for(Host host : this.getHosts()){
			for (IOFSwitch iswitch : this.getSwitches().values()){
				OFMatch match = new OFMatch();
				match.setDataLayerType(OFMatch.ETH_TYPE_IPV4);
				match.setNetworkDestination(host.getIPv4Address());
				SwitchCommands.removeRules(iswitch, this.table, match);
			}
			if(host.isAttachedToSwitch() == false) {
				return;
			}
			else{
				Map<Long, Integer> routes = generateRouterTable(host.getSwitch());
				OFMatch oMatch = new OFMatch();
				oMatch.setDataLayerType(OFMatch.ETH_TYPE_IPV4);
				oMatch.setNetworkDestination(host.getIPv4Address());
				for (Long id : routes.keySet()) 
				{
					OFAction oAction = new OFActionOutput(routes.get(id));
					OFInstruction oInstruction = new OFInstructionApplyActions(Arrays.asList(oAction));
					SwitchCommands.installRule(this.getSwitches().get(id), this.table,SwitchCommands.DEFAULT_PRIORITY, oMatch,Arrays.asList(oInstruction));
				}
				OFAction oAction = new OFActionOutput(host.getPort());
				OFInstruction oInstruction = new OFInstructionApplyActions(Arrays.asList(oAction));
				SwitchCommands.installRule(host.getSwitch(), this.table, SwitchCommands.DEFAULT_PRIORITY, oMatch, Arrays.asList(oInstruction));
			}
		}
		/*********************************************************************/
	}

	/**
	 * Event handler called when link goes up or down.
	 * @param update information about the change in link state
	 */
	@Override
	public void linkDiscoveryUpdate(LDUpdate update) 
	{ this.linkDiscoveryUpdate(Arrays.asList(update)); }
	
	/**
     * Event handler called when the IP address of a host changes.
     * @param device information about the host
     */
	@Override
	public void deviceIPV4AddrChanged(IDevice device) 
	{ this.deviceAdded(device); }

	/**
     * Event handler called when the VLAN of a host changes.
     * @param device information about the host
     */
	@Override
	public void deviceVlanChanged(IDevice device) 
	{ /* Nothing we need to do, since we're not using VLANs */ }
	
	/**
	 * Event handler called when the controller becomes the master for a switch.
	 * @param DPID for the switch
	 */
	@Override
	public void switchActivated(long switchId) 
	{ /* Nothing we need to do, since we're not switching controller roles */ }

	/**
	 * Event handler called when some attribute of a switch changes.
	 * @param DPID for the switch
	 */
	@Override
	public void switchChanged(long switchId) 
	{ /* Nothing we need to do */ }
	
	/**
	 * Event handler called when a port on a switch goes up or down, or is
	 * added or removed.
	 * @param DPID for the switch
	 * @param port the port on the switch whose status changed
	 * @param type the type of status change (up, down, add, remove)
	 */
	@Override
	public void switchPortChanged(long switchId, ImmutablePort port,
			PortChangeType type) 
	{ /* Nothing we need to do, since we'll get a linkDiscoveryUpdate event */ }

	/**
	 * Gets a name for this module.
	 * @return name for this module
	 */
	@Override
	public String getName() 
	{ return this.MODULE_NAME; }

	/**
	 * Check if events must be passed to another module before this module is
	 * notified of the event.
	 */
	@Override
	public boolean isCallbackOrderingPrereq(String type, String name) 
	{ return false; }

	/**
	 * Check if events must be passed to another module after this module has
	 * been notified of the event.
	 */
	@Override
	public boolean isCallbackOrderingPostreq(String type, String name) 
	{ return false; }
	
    /**
     * Tell the module system which services we provide.
     */
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() 
	{ return null; }

	/**
     * Tell the module system which services we implement.
     */
	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> 
			getServiceImpls() 
	{ return null; }

	/**
     * Tell the module system which modules we depend on.
     */
	@Override
	public Collection<Class<? extends IFloodlightService>> 
			getModuleDependencies() 
	{
		Collection<Class<? extends IFloodlightService >> floodlightService =
	            new ArrayList<Class<? extends IFloodlightService>>();
        floodlightService.add(IFloodlightProviderService.class);
        floodlightService.add(ILinkDiscoveryService.class);
        floodlightService.add(IDeviceService.class);
        return floodlightService;
	}
}