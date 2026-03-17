package com.rfid.integration.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for RFID tag detection events sent over WebSocket.
 *
 * CHANGED:
 * - Removed manual toJson() method: old implementation used raw string concatenation
 *   (e.g., "\"epc\":\"" + epc + "\"") which is an XSS/injection vector — EPC values from
 *   hardware or attacker-controlled readerIp from query string were never escaped.
 * - Added readerIp as a field set before serialization.
 * - Jackson ObjectMapper handles safe JSON serialization (escapes special characters).
 * - @JsonInclude(NON_NULL) omits null fields from output.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TagDataDTO {

    private String epc;
    private short antennaId;
    private short rssi;
    private long timestamp;
    private String readerIp;

    public TagDataDTO() {}

    public TagDataDTO(String epc, short antennaId, short rssi, long timestamp) {
        this.epc = epc;
        this.antennaId = antennaId;
        this.rssi = rssi;
        this.timestamp = timestamp;
    }

    public String getEpc() { return epc; }
    public void setEpc(String epc) { this.epc = epc; }

    public short getAntennaId() { return antennaId; }
    public void setAntennaId(short antennaId) { this.antennaId = antennaId; }

    public short getRssi() { return rssi; }
    public void setRssi(short rssi) { this.rssi = rssi; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getReaderIp() { return readerIp; }
    public void setReaderIp(String readerIp) { this.readerIp = readerIp; }
}
