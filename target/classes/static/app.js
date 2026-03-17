(function () {
    "use strict";

    /* ── DOM refs ── */
    var ipInput       = document.getElementById("reader-ip");
    var btnConnect    = document.getElementById("btn-connect");
    var btnStop       = document.getElementById("btn-stop");
    var btnDisconnect = document.getElementById("btn-disconnect");
    var btnClear      = document.getElementById("btn-clear");
    var btnClearLog   = document.getElementById("btn-clear-log");
    var healthBadge   = document.getElementById("health-badge");
    var readerStatus  = document.getElementById("reader-status");
    var wsStatus      = document.getElementById("ws-status");
    var tagCount      = document.getElementById("tag-count");
    var tagBody       = document.getElementById("tag-body");
    var logBox        = document.getElementById("log");

    /* ── State ── */
    var ws        = null;
    var readerIp  = "";
    var count     = 0;
    var rowNum    = 0;
    var MAX_ROWS  = 500;

    /* ── Helpers ── */
    function baseUrl() {
        return window.location.origin;
    }

    function setButtons(connect, stop, disconnect) {
        btnConnect.disabled    = !connect;
        btnDisconnect.disabled = !disconnect;
        btnStop.disabled       = !stop;
        ipInput.disabled       = !!ws;
    }

    function setReaderStatus(text) { readerStatus.textContent = text; }
    function setWsStatus(text)     { wsStatus.textContent = text; }
    function setTagCount(n)        { tagCount.textContent = n; }

    function logMsg(msg, level) {
        var span = document.createElement("span");
        span.className = "log-" + (level || "info");
        span.textContent = "[" + new Date().toLocaleTimeString() + "] " + msg;
        logBox.appendChild(span);
        logBox.appendChild(document.createElement("br"));
        logBox.scrollTop = logBox.scrollHeight;
    }

    function clearTable() {
        tagBody.innerHTML = "";
        count = 0;
        rowNum = 0;
        setTagCount(0);
    }

    function clearLog() {
        logBox.innerHTML = "";
    }

    function addTagRow(tag) {
        /* Remove empty placeholder if present */
        var empty = tagBody.querySelector(".empty-row");
        if (empty) empty.remove();

        rowNum++;
        count++;

        var tr = document.createElement("tr");
        tr.className = "new-row";

        var cells = [
            rowNum,
            tag.epc        || "—",
            tag.antennaId  != null ? tag.antennaId : "—",
            tag.rssi       != null ? tag.rssi      : "—",
            tag.timestamp  ? new Date(tag.timestamp).toLocaleTimeString() : "—"
        ];

        for (var i = 0; i < cells.length; i++) {
            var td = document.createElement("td");
            td.textContent = cells[i];
            tr.appendChild(td);
        }

        /* Keep table bounded */
        if (tagBody.rows.length >= MAX_ROWS) {
            tagBody.deleteRow(tagBody.rows.length - 1);
        }

        tagBody.insertBefore(tr, tagBody.firstChild);
        setTagCount(count);
    }

    /* ── API calls ── */
    function apiPost(path, successMsg) {
        var xhr = new XMLHttpRequest();
        xhr.open("POST", baseUrl() + path, true);
        xhr.onload = function () {
            if (xhr.status >= 200 && xhr.status < 300) {
                logMsg(successMsg, "ok");
            } else {
                logMsg("API error " + xhr.status + ": " + xhr.responseText, "error");
            }
        };
        xhr.onerror = function () {
            logMsg("Network error on " + path, "error");
        };
        xhr.send();
    }

    function apiGet(path, cb) {
        var xhr = new XMLHttpRequest();
        xhr.open("GET", baseUrl() + path, true);
        xhr.onload = function () { cb(xhr.status, xhr.responseText); };
        xhr.onerror = function () { cb(0, ""); };
        xhr.send();
    }

    /* ── WebSocket ── */
    function connectWs(ip) {
        if (ws) return;

        var proto = window.location.protocol === "https:" ? "wss:" : "ws:";
        var url   = proto + "//" + window.location.host + "/rfid-stream?readerIp=" + encodeURIComponent(ip);

        logMsg("Connecting WebSocket to " + ip + " …", "info");
        setWsStatus("Connecting…");

        ws = new WebSocket(url);

        ws.onopen = function () {
            setWsStatus("Connected");
            setReaderStatus("Connected");
            logMsg("WebSocket open", "ok");
            setButtons(false, true, true);

            /* Auto-start inventory */
            apiPost("/reader/start/" + encodeURIComponent(ip), "Inventory started on " + ip);
            setReaderStatus("Reading");
        };

        ws.onmessage = function (evt) {
            try {
                var tag = JSON.parse(evt.data);
                addTagRow(tag);
            } catch (e) {
                logMsg("Bad message: " + evt.data, "warn");
            }
        };

        ws.onerror = function () {
            logMsg("WebSocket error", "error");
        };

        ws.onclose = function (evt) {
            ws = null;
            setWsStatus("Disconnected");
            setReaderStatus("Disconnected");
            logMsg("WebSocket closed (code " + evt.code + ")", "warn");
            setButtons(true, false, false);
        };
    }

    function disconnectWs() {
        if (!ws) return;
        /* Stop inventory first, then close */
        if (readerIp) {
            apiPost("/reader/stop/" + encodeURIComponent(readerIp), "Inventory stopped");
        }
        ws.close();
    }

    /* ── Button handlers ── */
    btnConnect.addEventListener("click", function () {
        var ip = ipInput.value.trim();
        if (!ip) { logMsg("Enter a reader IP address", "warn"); return; }
        readerIp = ip;
        connectWs(ip);
    });

    btnStop.addEventListener("click", function () {
        if (!readerIp) return;
        apiPost("/reader/stop/" + encodeURIComponent(readerIp), "Inventory stopped on " + readerIp);
        setReaderStatus("Stopped");
    });

    btnDisconnect.addEventListener("click", function () {
        disconnectWs();
    });

    btnClear.addEventListener("click", clearTable);
    btnClearLog.addEventListener("click", clearLog);

    /* Enter key on input */
    ipInput.addEventListener("keydown", function (e) {
        if (e.key === "Enter") btnConnect.click();
    });

    /* ── Health polling ── */
    function checkHealth() {
        apiGet("/health", function (status) {
            if (status === 200) {
                healthBadge.textContent = "ONLINE";
                healthBadge.className = "badge badge-online";
            } else {
                healthBadge.textContent = "OFFLINE";
                healthBadge.className = "badge badge-offline";
            }
        });
    }

    checkHealth();
    setInterval(checkHealth, 10000);

    /* Initial UI state */
    setButtons(true, false, false);
    logMsg("Console ready", "info");
})();
