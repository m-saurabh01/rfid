package com.rfid.integration.model;

public class TagDataDTO {

    private String epc;
    private short antennaId;
    private short rssi;
    private long timestamp;

    public TagDataDTO() {}

    public TagDataDTO(String epc, short antennaId, short rssi, long timestamp) {
        this.epc = epc;
        this.antennaId = antennaId;
        this.rssi = rssi;
        this.timestamp = timestamp;
    }

    public String getEpc() {
        return epc;
    }

    public void setEpc(String epc) {
        this.epc = epc;
    }

    public short getAntennaId() {
        return antennaId;
    }

    public void setAntennaId(short antennaId) {
        this.antennaId = antennaId;
    }

    public short getRssi() {
        return rssi;
    }

    public void setRssi(short rssi) {
        this.rssi = rssi;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String toJson(String readerIp) {
        return "{"
                + "\"readerIp\":\"" + readerIp + "\","
                + "\"epc\":\"" + epc + "\","
                + "\"antennaId\":" + antennaId + ","
                + "\"rssi\":" + rssi + ","
                + "\"timestamp\":" + timestamp
                + "}";
    }
}
