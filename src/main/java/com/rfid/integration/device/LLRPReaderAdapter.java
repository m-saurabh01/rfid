package com.rfid.integration.device;

import com.rfid.integration.model.TagDataDTO;
import org.llrp.ltk.net.LLRPConnector;
import org.llrp.ltk.net.LLRPEndpoint;
import org.llrp.ltk.generated.messages.*;
import org.llrp.ltk.generated.parameters.*;
import org.llrp.ltk.generated.enumerations.*;
import org.llrp.ltk.generated.interfaces.SpecParameter;
import org.llrp.ltk.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * LLRP protocol adapter — communicates with physical RFID readers via TCP.
 *
 * CHANGED from original:
 *
 * 1. CONNECTION TIMEOUT: connect(CONNECT_TIMEOUT_MS) instead of blocking indefinitely.
 *    Old code blocked the calling thread for the TCP default timeout (75-120s on Linux).
 *    Now fails fast in 10s.
 *
 * 2. TRANSACT TIMEOUT: transact(msg, TRANSACT_TIMEOUT_MS) on all LLRP commands.
 *    Prevents indefinite blocking if the reader stops responding mid-session.
 *
 * 3. Thread.sleep(100) in stopInventory(): Kept but reduced from 150ms. Required by
 *    some readers that need time between state transitions. The synchronized lock is
 *    held for ~200ms + network RTT. This is acceptable because stop is a rare,
 *    user-initiated operation (not called from hot paths).
 *
 * 4. SLF4J logging replaces System.out.println throughout.
 *
 * 5. Null check on getTagReportDataList() in messageReceived() — prevents NPE
 *    on empty LLRP reports.
 *
 * 6. Per-tag exception handling so one malformed tag doesn't kill the entire report.
 *
 * Prior fixes preserved:
 * - implements LLRPEndpoint (not nonexistent LLRPMessageListener)
 * - new Bit(1/0) instead of boolean for TagReportContentSelector
 * - EPCParameter.toString() instead of nonexistent getEPC()
 * - intValue() instead of nonexistent shortValue() on UnsignedShort/SignedByte
 * - volatile boolean connected field
 */
public class LLRPReaderAdapter implements LLRPEndpoint {

    private static final Logger log = LoggerFactory.getLogger(LLRPReaderAdapter.class);

    private LLRPConnector connector;
    private final String readerIp;
    private final Consumer<TagDataDTO> tagCallback;
    private volatile boolean connected = false;

    private static final int ROSPEC_ID = 111;
    private static final long CONNECT_TIMEOUT_MS = 10_000;
    private static final long TRANSACT_TIMEOUT_MS = 5_000;

    public LLRPReaderAdapter(String readerIp, Consumer<TagDataDTO> tagCallback) {
        this.readerIp = readerIp;
        this.tagCallback = tagCallback;
    }

    // ======================== CONNECT ========================

    public synchronized void connect() throws Exception {
        if (connector != null && connected) {
            return;
        }
        connector = new LLRPConnector(this, readerIp, 5084);
        connector.connect(CONNECT_TIMEOUT_MS);
        deleteAllROSpecs();
        connected = true;
        log.info("Connected to reader {}", readerIp);
    }

    // ======================== START INVENTORY ========================

    public synchronized void startInventory() throws Exception {
        ADD_ROSPEC addROSpec = new ADD_ROSPEC();
        addROSpec.setROSpec(createROSpec());
        connector.transact(addROSpec, TRANSACT_TIMEOUT_MS);

        ENABLE_ROSPEC enable = new ENABLE_ROSPEC();
        enable.setROSpecID(new UnsignedInteger(ROSPEC_ID));
        connector.transact(enable, TRANSACT_TIMEOUT_MS);

        START_ROSPEC start = new START_ROSPEC();
        start.setROSpecID(new UnsignedInteger(ROSPEC_ID));
        connector.transact(start, TRANSACT_TIMEOUT_MS);
        log.info("Inventory started on reader {}", readerIp);
    }

    // ======================== STOP INVENTORY ========================

    public synchronized void stopInventory() throws Exception {
        STOP_ROSPEC stop = new STOP_ROSPEC();
        stop.setROSpecID(new UnsignedInteger(ROSPEC_ID));
        connector.transact(stop, TRANSACT_TIMEOUT_MS);

        Thread.sleep(100);

        DISABLE_ROSPEC disable = new DISABLE_ROSPEC();
        disable.setROSpecID(new UnsignedInteger(ROSPEC_ID));
        connector.transact(disable, TRANSACT_TIMEOUT_MS);

        Thread.sleep(100);

        DELETE_ROSPEC delete = new DELETE_ROSPEC();
        delete.setROSpecID(new UnsignedInteger(ROSPEC_ID));
        connector.transact(delete, TRANSACT_TIMEOUT_MS);
        log.info("Inventory stopped on reader {}", readerIp);
    }

    // ======================== DISCONNECT ========================

    public synchronized void disconnect() {
        try {
            stopInventory();
        } catch (Exception e) {
            log.debug("Stop inventory during disconnect: {}", e.getMessage());
        }
        try {
            if (connector != null) {
                connector.disconnect();
            }
        } catch (Exception e) {
            log.debug("Connector disconnect: {}", e.getMessage());
        }
        connector = null;
        connected = false;
        log.info("Disconnected from reader {}", readerIp);
    }

    public boolean isConnected() {
        return connected && connector != null;
    }

    // ======================== CREATE ROSPEC ========================

    private ROSpec createROSpec() {
        ROSpec roSpec = new ROSpec();
        roSpec.setROSpecID(new UnsignedInteger(ROSPEC_ID));
        roSpec.setPriority(new UnsignedByte(0));
        roSpec.setCurrentState(new ROSpecState(ROSpecState.Disabled));

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

        AISpec aiSpec = new AISpec();
        UnsignedShortArray antennas = new UnsignedShortArray();
        antennas.add(new UnsignedShort(0));
        aiSpec.setAntennaIDs(antennas);

        AISpecStopTrigger aiStop = new AISpecStopTrigger();
        aiStop.setAISpecStopTriggerType(
                new AISpecStopTriggerType(AISpecStopTriggerType.Null));
        aiSpec.setAISpecStopTrigger(aiStop);

        InventoryParameterSpec inventory = new InventoryParameterSpec();
        inventory.setInventoryParameterSpecID(new UnsignedShort(1));
        inventory.setProtocolID(new AirProtocols(AirProtocols.EPCGlobalClass1Gen2));
        aiSpec.addToInventoryParameterSpecList(inventory);

        ROReportSpec report = new ROReportSpec();
        report.setROReportTrigger(
                new ROReportTriggerType(ROReportTriggerType.Upon_N_Tags_Or_End_Of_ROSpec));
        report.setN(new UnsignedShort(1));

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

    // ======================== DELETE OLD ROSPECS ========================

    private void deleteAllROSpecs() throws Exception {
        DELETE_ROSPEC delete = new DELETE_ROSPEC();
        delete.setROSpecID(new UnsignedInteger(0));
        connector.transact(delete, TRANSACT_TIMEOUT_MS);
    }

    // ======================== MESSAGE LISTENER ========================

    @Override
    public void messageReceived(LLRPMessage message) {
        if (message instanceof KEEPALIVE) {
            KEEPALIVE_ACK ack = new KEEPALIVE_ACK();
            connector.send(ack);
            return;
        }

        if (message instanceof RO_ACCESS_REPORT) {
            RO_ACCESS_REPORT report = (RO_ACCESS_REPORT) message;
            if (report.getTagReportDataList() == null) return;

            for (TagReportData tag : report.getTagReportDataList()) {
                try {
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
                } catch (Exception e) {
                    log.warn("Error processing tag report: {}", e.getMessage());
                }
            }
        }
    }

    @Override
    public void errorOccured(String s) {
        log.error("LLRP error on reader {}: {}", readerIp, s);
    }
}
