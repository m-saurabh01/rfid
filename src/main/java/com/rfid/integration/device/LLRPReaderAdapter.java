package com.rfid.integration.device;

import com.rfid.integration.model.TagDataDTO;
import org.llrp.ltk.net.LLRPConnector;
import org.llrp.ltk.net.LLRPEndpoint;
import org.llrp.ltk.generated.messages.*;
import org.llrp.ltk.generated.parameters.*;
import org.llrp.ltk.generated.enumerations.*;
import org.llrp.ltk.generated.interfaces.SpecParameter;
import org.llrp.ltk.types.*;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class LLRPReaderAdapter implements LLRPEndpoint {

    private LLRPConnector connector;
    private final String readerIp;
    private final Consumer<TagDataDTO> tagCallback;
    private volatile boolean connected = false;

    private static final int ROSPEC_ID = 111;

    public LLRPReaderAdapter(String readerIp, Consumer<TagDataDTO> tagCallback) {
        this.readerIp = readerIp;
        this.tagCallback = tagCallback;
    }

    // ---------------- CONNECT ----------------

    public synchronized void connect() throws Exception {
        if (connector != null && connected) {
            return;
        }
        connector = new LLRPConnector(this, readerIp, 5084);
        connector.connect();
        deleteAllROSpecs();
        connected = true;
    }

    // ---------------- START INVENTORY ----------------

    public synchronized void startInventory() throws Exception {
        ADD_ROSPEC addROSpec = new ADD_ROSPEC();
        addROSpec.setROSpec(createROSpec());
        connector.transact(addROSpec);

        ENABLE_ROSPEC enable = new ENABLE_ROSPEC();
        enable.setROSpecID(new UnsignedInteger(ROSPEC_ID));
        connector.transact(enable);

        START_ROSPEC start = new START_ROSPEC();
        start.setROSpecID(new UnsignedInteger(ROSPEC_ID));
        connector.transact(start);
    }

    // ---------------- STOP INVENTORY ----------------

    public synchronized void stopInventory() throws Exception {
        STOP_ROSPEC stop = new STOP_ROSPEC();
        stop.setROSpecID(new UnsignedInteger(ROSPEC_ID));
        connector.transact(stop);

        Thread.sleep(150);

        DISABLE_ROSPEC disable = new DISABLE_ROSPEC();
        disable.setROSpecID(new UnsignedInteger(ROSPEC_ID));
        connector.transact(disable);

        Thread.sleep(150);

        DELETE_ROSPEC delete = new DELETE_ROSPEC();
        delete.setROSpecID(new UnsignedInteger(ROSPEC_ID));
        connector.transact(delete);
    }

    // ---------------- DISCONNECT ----------------

    public synchronized void disconnect() {
        try {
            stopInventory();
        } catch (Exception ignored) {}
        try {
            if (connector != null) {
                connector.disconnect();
            }
        } catch (Exception ignored) {}
        connector = null;
        connected = false;
    }

    public boolean isConnected() {
        return connected && connector != null;
    }

    // ---------------- CREATE ROSPEC ----------------

    private ROSpec createROSpec() {
        ROSpec roSpec = new ROSpec();
        roSpec.setROSpecID(new UnsignedInteger(ROSPEC_ID));
        roSpec.setPriority(new UnsignedByte(0));
        roSpec.setCurrentState(new ROSpecState(ROSpecState.Disabled));

        // Start / Stop Triggers
        ROSpecStartTrigger startTrigger = new ROSpecStartTrigger();
        startTrigger.setROSpecStartTriggerType(
                new ROSpecStartTriggerType(ROSpecStartTriggerType.Null));

        ROSpecStopTrigger stopTrigger = new ROSpecStopTrigger();
        stopTrigger.setROSpecStopTriggerType(
                new ROSpecStopTriggerType(ROSpecStopTriggerType.Null));

        ROBoundarySpec boundary = new ROBoundarySpec();
        boundary.setROSpecStartTrigger(startTrigger);
        boundary.setROSpecStopTrigger(stopTrigger);
        roSpec.setROBoundarySpec(boundary);

        // AISpec (antenna inventory)
        AISpec aiSpec = new AISpec();
        UnsignedShortArray antennas = new UnsignedShortArray();
        antennas.add(new UnsignedShort(0)); // 0 = all antennas
        aiSpec.setAntennaIDs(antennas);

        AISpecStopTrigger aiStop = new AISpecStopTrigger();
        aiStop.setAISpecStopTriggerType(
                new AISpecStopTriggerType(AISpecStopTriggerType.Null));
        aiSpec.setAISpecStopTrigger(aiStop);

        // Inventory Parameter
        InventoryParameterSpec inventory = new InventoryParameterSpec();
        inventory.setInventoryParameterSpecID(new UnsignedShort(1));
        inventory.setProtocolID(new AirProtocols(AirProtocols.EPCGlobalClass1Gen2));
        aiSpec.addToInventoryParameterSpecList(inventory);

        // Report Spec
        ROReportSpec report = new ROReportSpec();
        report.setROReportTrigger(
                new ROReportTriggerType(ROReportTriggerType.Upon_N_Tags_Or_End_Of_ROSpec));
        report.setN(new UnsignedShort(1));

        // Tag Content Selector
        TagReportContentSelector selector = new TagReportContentSelector();
        selector.setEnableAntennaID(new Bit(1));
        selector.setEnableChannelIndex(new Bit(0));
        selector.setEnableFirstSeenTimestamp(new Bit(1));
        selector.setEnableInventoryParameterSpecID(new Bit(0));
        selector.setEnableLastSeenTimestamp(new Bit(0));
        selector.setEnablePeakRSSI(new Bit(1));
        selector.setEnableROSpecID(new Bit(0));
        selector.setEnableSpecIndex(new Bit(0));
        selector.setEnableTagSeenCount(new Bit(1));
        report.setTagReportContentSelector(selector);

        roSpec.setROReportSpec(report);
        List<SpecParameter> specList = Arrays.asList((SpecParameter) aiSpec);
        roSpec.setSpecParameterList(specList);

        return roSpec;
    }

    // ---------------- DELETE OLD ROSPECS ----------------

    private void deleteAllROSpecs() throws Exception {
        DELETE_ROSPEC delete = new DELETE_ROSPEC();
        delete.setROSpecID(new UnsignedInteger(0));
        connector.transact(delete);
    }

    // ---------------- MESSAGE LISTENER ----------------

    @Override
    public void messageReceived(LLRPMessage message) {
        if (message instanceof KEEPALIVE) {
            KEEPALIVE_ACK ack = new KEEPALIVE_ACK();
            connector.send(ack);
        }

        if (message instanceof RO_ACCESS_REPORT) {
            RO_ACCESS_REPORT report = (RO_ACCESS_REPORT) message;

            for (TagReportData tag : report.getTagReportDataList()) {
                // EPCParameter interface has no getEPC(); toString() works for both EPC_96 and EPCData
                String epc = tag.getEPCParameter().toString();

                short antenna = 0;
                short rssi = 0;

                if (tag.getAntennaID() != null) {
                    antenna = (short) tag.getAntennaID().getAntennaID().intValue();
                }
                if (tag.getPeakRSSI() != null) {
                    rssi = (short) tag.getPeakRSSI().getPeakRSSI().intValue();
                }

                TagDataDTO dto = new TagDataDTO(epc, antenna, rssi, System.currentTimeMillis());
                tagCallback.accept(dto);
            }
        }
    }

    @Override
    public void errorOccured(String s) {
        System.out.println("LLRP Error: " + s);
    }
}
