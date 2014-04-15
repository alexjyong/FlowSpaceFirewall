/*
 Copyright 2013 Trustees of Indiana University

   Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package edu.iu.grnoc.flowspace_firewall;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.junit.Assert.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packetstreamer.thrift.OFMessageType;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.socket.SocketChannel;
import org.junit.Rule;
import org.junit.Test;
import org.junit.Before;
import org.junit.rules.ExpectedException;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.action.OFActionType;

import org.openflow.protocol.action.OFActionVirtualLanIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyTest {

	protected static Logger log = LoggerFactory.getLogger(ProxyTest.class);
	
	@Rule
	public ExpectedException thrown = ExpectedException.none();
	
	private IOFSwitch sw;
	private VLANSlicer slicer;
	private FlowSpaceFirewall fsfw;
	private SocketChannel channel;
	OFControllerChannelHandler handler;
	FloodlightContext cntx;
	
	private List<OFMessage> messagesSentToController;
	private List<OFMessage> messagesSentToSwitch;
	
	public void setupChannel() throws IOException{
		ChannelFuture future = createMock(org.jboss.netty.channel.ChannelFuture.class);
		ChannelPipeline pipeline = createMock(org.jboss.netty.channel.ChannelPipeline.class);
		ChannelHandlerContext context = createMock(org.jboss.netty.channel.ChannelHandlerContext.class);
		handler = EasyMock.createMock(edu.iu.grnoc.flowspace_firewall.OFControllerChannelHandler.class);
		channel = EasyMock.createNiceMock(org.jboss.netty.channel.socket.SocketChannel.class);
		
		ChannelFuture otherFuture = createMock(org.jboss.netty.channel.ChannelFuture.class);
		expect(channel.getPipeline()).andReturn(pipeline).anyTimes();
		expect(pipeline.getContext("handler")).andReturn(context).anyTimes();
		expect(context.getHandler()).andReturn(handler).anyTimes();
		expect(channel.connect(EasyMock.isA(java.net.InetSocketAddress.class))).andReturn(future).anyTimes();
		expect(channel.write(EasyMock.isA(org.openflow.protocol.OFMessage.class))).andReturn(otherFuture).anyTimes();
		
		handler.setSwitch(EasyMock.isA(net.floodlightcontroller.core.IOFSwitch.class));
		EasyMock.expectLastCall().anyTimes();
		
		handler.setProxy(EasyMock.isA(edu.iu.grnoc.flowspace_firewall.Proxy.class));
		EasyMock.expectLastCall().anyTimes();
		
		handler.sendMessage(EasyMock.isA(org.openflow.protocol.OFMessage.class));
		EasyMock.expectLastCall().andAnswer(new IAnswer<Object>() {
		    public Object answer() {
		        //supply your mock implementation here...
		        messagesSentToController.add((OFMessage)EasyMock.getCurrentArguments()[0]);
		        //return the value to be returned by the method (null for void)
		        return null;
		    }
		}).anyTimes();
		
		EasyMock.replay(future);
		EasyMock.replay(pipeline);
		EasyMock.replay(context);
		EasyMock.replay(handler);
		EasyMock.replay(otherFuture);
	}
	
	public void setupSlicer(){
		
        slicer = new VLANSlicer();
		
		PortConfig pConfig = new PortConfig();
		pConfig.setPortName("foo");
		VLANRange range = new VLANRange();
		range.setVlanAvail((short)100,true);
		range.setVlanAvail((short)1000,true);
		pConfig.setVLANRange(range);
		slicer.setPortConfig("foo", pConfig);
		
		PortConfig pConfig2 = new PortConfig();
		pConfig2.setPortName("foo2");
		range = new VLANRange();
		range.setVlanAvail((short)102,true);
		range.setVlanAvail((short)1000,true);
		pConfig2.setVLANRange(range);
		slicer.setPortConfig("foo2", pConfig2);

		PortConfig pConfig3 = new PortConfig();
		pConfig3.setPortName("foo3");
		range = new VLANRange();
		range.setVlanAvail((short)103,true);
		range.setVlanAvail((short)1000,true);
		pConfig3.setVLANRange(range);
		slicer.setPortConfig("foo3", pConfig3);
		
		PortConfig pConfig5 = new PortConfig();
		pConfig5.setPortName("foo5");
		range = new VLANRange();
		range.setVlanAvail((short)105,true);
		range.setVlanAvail((short)1000,true);
		pConfig5.setVLANRange(range);
		slicer.setPortConfig("foo5", pConfig5);
		
		PortConfig pConfig6 = new PortConfig();
		pConfig6.setPortName("foo6");
		range = new VLANRange();
		range.setVlanAvail((short)105,true);
		range.setVlanAvail((short)1000,true);
		pConfig6.setVLANRange(range);
		slicer.setPortConfig("foo6", pConfig6);
		slicer.setMaxFlows(2);
		slicer.setController(new InetSocketAddress("globalnoc.iu.edu", 6633));
		
	}
	
	public void setupSwitch() throws IOException{
		ArrayList <ImmutablePort> ports = new ArrayList <ImmutablePort>();
		
		ImmutablePort p = createMock(ImmutablePort.class);
		expect(p.getName()).andReturn("foo").anyTimes();
		expect(p.getPortNumber()).andReturn((short)1).anyTimes();
		EasyMock.replay(p);
		ports.add(p);
		
		ImmutablePort p2 = createMock(ImmutablePort.class);
		expect(p2.getName()).andReturn("foo2").anyTimes();
		expect(p2.getPortNumber()).andReturn((short)2).anyTimes();
		EasyMock.replay(p2);
		ports.add(p2);
		
		ImmutablePort p3 = createMock(ImmutablePort.class);
		expect(p3.getName()).andReturn("foo3").anyTimes();
		expect(p3.getPortNumber()).andReturn((short)3).anyTimes();
		EasyMock.replay(p3);
		ports.add(p3);
		
		ImmutablePort p4 = createMock(ImmutablePort.class);
		expect(p4.getName()).andReturn("foo4").anyTimes();
		expect(p4.getPortNumber()).andReturn((short)4).anyTimes();
		EasyMock.replay(p4);
		ports.add(p4);
		
		ImmutablePort p5 = createMock(ImmutablePort.class);
		expect(p5.getName()).andReturn("foo5").anyTimes();
		expect(p5.getPortNumber()).andReturn((short)5).anyTimes();
		EasyMock.replay(p5);
		ports.add(p5);
		
		ImmutablePort p6 = createMock(ImmutablePort.class);
		expect(p6.getName()).andReturn("foo6").anyTimes();
		expect(p6.getPortNumber()).andReturn((short)59590).anyTimes();
		EasyMock.replay(p6);
		ports.add(p6);
		
		sw = EasyMock.createNiceMock(IOFSwitch.class);
		expect(sw.getId()).andReturn(0L).anyTimes();
		expect(sw.getPort((short)1)).andReturn(p).anyTimes();
		expect(sw.getPort((short)2)).andReturn(p2).anyTimes();
		expect(sw.getPort((short)3)).andReturn(p3).anyTimes();
		expect(sw.getPort((short)4)).andReturn(p4).anyTimes();
		expect(sw.getPort((short)5)).andReturn(p5).anyTimes();
		expect(sw.getPort((short)100)).andReturn(null).anyTimes();
		expect(sw.getPort((short)59590)).andReturn(p6).anyTimes();
		expect(sw.getPort((short)-1)).andReturn(null).anyTimes();
        expect(sw.getPorts()).andReturn((Collection <ImmutablePort>) ports).anyTimes();
        
        expect(sw.getNextTransactionId()).andReturn(1).once().andReturn(2).once()
		.andReturn(3).once().andReturn(4).once().andReturn(5).once().andReturn(6).once();
        sw.write(EasyMock.isA(org.openflow.protocol.OFMessage.class), EasyMock.isA(net.floodlightcontroller.core.FloodlightContext.class));
        EasyMock.expectLastCall().andAnswer(new IAnswer<Object>() {
		    public Object answer() {
		    	log.debug("Here!");
		        //supply your mock implementation here...
		        messagesSentToSwitch.add((OFMessage)EasyMock.getCurrentArguments()[0]);
		        //return the value to be returned by the method (null for void)
		        return null;
		    }
		}).anyTimes();
        
        EasyMock.replay(sw);
	}
	
	public void setupFSFW(){
		fsfw = createMock(FlowSpaceFirewall.class);
		
	}
	
	@Before
	public void setUp(){
		
		messagesSentToController = new ArrayList<OFMessage>();
		messagesSentToSwitch = new ArrayList<OFMessage>();
		cntx = new FloodlightContext();
		setupFSFW();
		
		try {
			setupChannel();
			setupSwitch();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		setupSlicer();
		
		
	}
	
	@Test
	public void testInstantiate() {
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		assertNotNull("Proxy was created", proxy);
		assertNotNull("Proxy can find portConfig by ID",proxy.getSlicer().getPortConfig((short)1));
	}
	
	@Test
	public void testConnect(){
		expect(channel.isConnected()).andReturn(true).once().andReturn(false).once();
		EasyMock.replay(channel);
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		proxy.disconnect();
		assertFalse("Proxy is now disconnected", proxy.connected());
	}
	
	@Test
	public void testFlowModAllowedTest(){
		messagesSentToSwitch.clear();
		messagesSentToController.clear();
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		OFFlowMod flow = new OFFlowMod();
		flow.setCommand(OFFlowMod.OFPFC_ADD);
		OFMatch match = new OFMatch();
		match.setDataLayerVirtualLan((short)100);
		match.setInputPort((short)1);
		List<OFAction> actions = new ArrayList<OFAction>();
		OFActionVirtualLanIdentifier act1 = new OFActionVirtualLanIdentifier();
		act1.setVirtualLanIdentifier((short)102);
		OFActionOutput act2 = new OFActionOutput();
		act2.setPort((short)2);
		flow.setMatch(match);
		actions.add(act1);
		actions.add(act2);
		flow.setActions(actions);
		proxy.toSwitch(flow, cntx);
		assertTrue("Flow was successfully pushed", proxy.getFlowCount() == 1);
		assertTrue("Flow was pushed to the switch", messagesSentToSwitch.size() == 1);
		OFMessage msg = messagesSentToSwitch.get(0);
		assertTrue("Message is a FlowMod", msg.getType().getTypeValue() == OFMessageType.FLOW_MOD.getValue());
		OFFlowMod sentFlow = (OFFlowMod) msg;
		assertTrue("Sent Flow matches what we actually sent", sentFlow.equals(flow));
	}

	@Test
	public void testFlowModNotAllowedTest(){
		setupSlicer();
		messagesSentToSwitch.clear();
		messagesSentToController.clear();
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		
		//build the match
		OFMatch match = new OFMatch();
		match.setDataLayerVirtualLan((short)100);
		match.setInputPort((short)1);
		List<OFAction> actions = new ArrayList<OFAction>();
				
		//create the output action
		OFActionOutput act2 = new OFActionOutput();
		act2.setPort((short)2);
		act2.setType(OFActionType.OUTPUT);
		
		//add the actions to the action list
		actions.add(act2);
		
		//build the flow
		OFFlowMod flow = new OFFlowMod();
		flow.setCommand(OFFlowMod.OFPFC_ADD);
		flow.setXid(101);
		flow.setMatch(match);
		flow.setActions(actions);
		flow.setLengthU(80);
		//send it
		proxy.toSwitch((OFMessage)flow, cntx);
		assertTrue("Flow as not pushed... have " + proxy.getFlowCount() + " flows", proxy.getFlowCount() == 0);
		
	}
	
	@Test
	public void testFlowModMaxLimit(){
		setupSlicer();
		messagesSentToSwitch.clear();
		messagesSentToController.clear();
		slicer.setMaxFlows(4);
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		
		//build the match
		OFMatch match = new OFMatch();
		match.setDataLayerVirtualLan((short)100);
		match.setInputPort((short)1);
		List<OFAction> actions = new ArrayList<OFAction>();
				
		//create the output action
		OFActionOutput act2 = new OFActionOutput();
		act2.setPort((short)1);
		act2.setType(OFActionType.OUTPUT);
		
		//add the actions to the action list
		actions.add(act2);
				
		//build the flow
		OFFlowMod flow = new OFFlowMod();
		flow.setCommand(OFFlowMod.OFPFC_ADD);
		flow.setXid(101);
		flow.setMatch(match);
		flow.setActions(actions);
		flow.setLengthU(80);
		//send it
		proxy.toSwitch((OFMessage)flow, cntx);
		proxy.toSwitch((OFMessage)flow, cntx);
		proxy.toSwitch((OFMessage)flow, cntx);
		proxy.toSwitch((OFMessage)flow, cntx);
		proxy.toSwitch((OFMessage)flow, cntx);
		assertTrue("4 flows pushed 1 denied because over max limit " + proxy.getFlowCount() + " flows", proxy.getFlowCount() == 4);
		
	}
	
	
	@Test
	public void testPacketOut(){
		setupSlicer();
		messagesSentToSwitch.clear();
		messagesSentToController.clear();
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		
		OFPacketOut out = new OFPacketOut();
		List<OFAction> actions = new ArrayList<OFAction>();
		OFActionOutput output = new OFActionOutput();
		output.setType(OFActionType.OUTPUT);
		output.setPort((short)1);
		actions.add(output);
		out.setActions(actions);
		
		Ethernet pkt = new Ethernet();
		pkt.setVlanID((short)1000);
		pkt.setDestinationMACAddress("aa:bb:cc:dd:ee:ff");
		pkt.setSourceMACAddress("ff:ee:dd:cc:bb:aa");
		pkt.setEtherType((short)35020);
		out.setPacketData(pkt.serialize());
		
		proxy.toSwitch(out, cntx);
		
	}
	
	
	@Test
	public void testPacketOutDeny(){
		setupSlicer();
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		
		OFPacketOut out = new OFPacketOut();
		List<OFAction> actions = new ArrayList<OFAction>();
		OFActionOutput output = new OFActionOutput();
		output.setType(OFActionType.OUTPUT);
		output.setPort((short)1);
		actions.add(output);
		out.setActions(actions);
		
		Ethernet pkt = new Ethernet();
		pkt.setVlanID((short)3000);
		pkt.setDestinationMACAddress("aa:bb:cc:dd:ee:ff");
		pkt.setSourceMACAddress("ff:ee:dd:cc:bb:aa");
		pkt.setEtherType((short)35020);
		out.setPacketData(pkt.serialize());
		//TODO: figure out the right size to set this too... this works for now
		out.setLengthU(out.getPacketData().length + 40);
		
		proxy.toSwitch(out, cntx);
		
	}
	
	@Test
	public void testPacketOutLimit() throws InterruptedException{
		setupSlicer();
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		
		OFPacketOut out = new OFPacketOut();
		List<OFAction> actions = new ArrayList<OFAction>();
		OFActionOutput output = new OFActionOutput();
		output.setType(OFActionType.OUTPUT);
		output.setPort((short)1);
		actions.add(output);
		out.setActions(actions);
		
		Ethernet pkt = new Ethernet();
		pkt.setVlanID((short)1000);
		pkt.setDestinationMACAddress("aa:bb:cc:dd:ee:ff");
		pkt.setSourceMACAddress("ff:ee:dd:cc:bb:aa");
		pkt.setEtherType((short)35020);
		out.setPacketData(pkt.serialize());
		
		//TODO: figure out the right size to set this too... this works for now
		out.setLengthU(out.getPacketData().length + 40);
		slicer.setFlowRate(2);
		
		
		for(int i=0;i<10;i++){
			java.lang.Thread.sleep(250);
			double rate = proxy.getSlicer().getRate();
			proxy.toSwitch(out, cntx);
			if(rate >= 2){
				//we should not let this go through
				//how to verify we got an error back?
				
			}
			
			
			
		}
		
		//figure out how to tell if a packet got denied because of 
		
	}
	
	@Test
	public void testPortMod(){
		setupSlicer();
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		
		
	}
	
	@Test
	public void testSetConfig(){
		setupSlicer();
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		
		
	}

	@Test
	public void testBarrierReply(){
		setupSlicer();
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		
		
	}
	
	@Test
	public void testErrorReturned(){
		setupSlicer();
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		
		
	}
	
	@Test
	public void testErrorReturnedNotPartOfSlice(){
		setupSlicer();
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		
		
	}
	
	@Test
	public void testFlowRemoved(){
		setupSlicer();
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		
		
	}
	
	@Test
	public void testFlowRemovedNotPartOfSlice(){
		setupSlicer();
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		
		
	}
	
	@Test
	public void testPacketINPartOfSlice(){
		setupSlicer();
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		
		
	}
	
	@Test
	public void testPacketINNotPartofSlice(){
		setupSlicer();
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		
		
	}
	
	@Test
	public void testPortStatus(){
		setupSlicer();
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		
		
	}
	
	@Test
	public void testPortStatusNotPartOfSlice(){
		setupSlicer();
		Proxy proxy = new Proxy(sw, slicer, fsfw);
		expect(channel.isConnected()).andReturn(true).anyTimes();
		EasyMock.replay(channel);
		assertNotNull("Proxy was created",proxy);
		assertFalse("Proxy is not connected as expected", proxy.connected());
		proxy.connect(channel);
		assertTrue("Proxy is now connected", proxy.connected());
		
		
	}
	
	@Test
	public void testFlowStatsRequest(){
		
	}
}

