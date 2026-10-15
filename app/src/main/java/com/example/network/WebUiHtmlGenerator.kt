package com.example.network

import com.example.model.WebSharedFile
import org.json.JSONArray
import org.json.JSONObject

object WebUiHtmlGenerator {

    fun generateFilesJson(files: List<WebSharedFile>, deviceName: String): String {
        val root = JSONObject()
        root.put("deviceName", deviceName)
        val array = JSONArray()
        for (file in files) {
            val obj = JSONObject()
            obj.put("id", file.id)
            obj.put("name", file.name)
            obj.put("size", file.sizeBytes)
            obj.put("formattedSize", file.formattedSize)
            obj.put("isApk", file.isApk)
            obj.put("mimeType", file.mimeType)
            array.put(obj)
        }
        root.put("files", array)
        return root.toString()
    }

    private fun escapeHtml(str: String): String {
        return str
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#039;")
    }

    fun getWebPageHtml(deviceName: String, initialFiles: List<WebSharedFile>): String {
        val safeDeviceName = escapeHtml(deviceName)
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>BeamDrop Web Share</title>
    <style>
        :root {
            --bg: #090e1a;
            --surface: #121c33;
            --surface-variant: #1a2747;
            --border: #22355e;
            --cyan: #00e5ff;
            --cyan-glow: rgba(0, 229, 255, 0.25);
            --green: #10b981;
            --green-glow: rgba(16, 185, 129, 0.2);
            --text-primary: #f8fafc;
            --text-secondary: #94a3b8;
            --card-radius: 18px;
        }

        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
        }

        body {
            font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
            background: var(--bg);
            color: var(--text-primary);
            line-height: 1.5;
            min-height: 100vh;
            padding: 24px 16px;
            display: flex;
            flex-direction: column;
            align-items: center;
        }

        .container {
            width: 100%;
            max-width: 820px;
            display: flex;
            flex-direction: column;
            gap: 20px;
        }

        header {
            background: linear-gradient(135deg, var(--surface) 0%, #15223e 100%);
            border: 1px solid var(--border);
            border-radius: var(--card-radius);
            padding: 24px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            flex-wrap: wrap;
            gap: 16px;
            box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.5);
        }

        .brand-section {
            display: flex;
            align-items: center;
            gap: 14px;
        }

        .logo-box {
            width: 48px;
            height: 48px;
            background: rgba(0, 229, 255, 0.15);
            border: 1px solid var(--cyan);
            border-radius: 14px;
            display: flex;
            align-items: center;
            justify-content: center;
            color: var(--cyan);
            font-size: 24px;
            box-shadow: 0 0 15px var(--cyan-glow);
        }

        .brand-title {
            font-size: 22px;
            font-weight: 800;
            letter-spacing: -0.5px;
        }

        .brand-sub {
            font-size: 13px;
            color: var(--text-secondary);
        }

        .status-pill {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            background: var(--green-glow);
            border: 1px solid rgba(16, 185, 129, 0.4);
            color: var(--green);
            padding: 6px 14px;
            border-radius: 30px;
            font-size: 13px;
            font-weight: 600;
        }

        .status-dot {
            width: 8px;
            height: 8px;
            background: var(--green);
            border-radius: 50%;
            animation: pulse 2s infinite;
        }

        @keyframes pulse {
            0% { transform: scale(0.9); opacity: 0.8; }
            50% { transform: scale(1.3); opacity: 1; }
            100% { transform: scale(0.9); opacity: 0.8; }
        }

        .card {
            background: var(--surface);
            border: 1px solid var(--border);
            border-radius: var(--card-radius);
            padding: 24px;
            box-shadow: 0 6px 18px rgba(0, 0, 0, 0.3);
        }

        .card-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 16px;
        }

        .card-title {
            font-size: 17px;
            font-weight: 700;
            display: flex;
            align-items: center;
            gap: 8px;
        }

        /* Upload Area */
        .dropzone {
            border: 2px dashed var(--border);
            border-radius: 16px;
            padding: 36px 20px;
            text-align: center;
            cursor: pointer;
            transition: all 0.2s ease;
            background: rgba(18, 28, 51, 0.5);
        }

        .dropzone:hover, .dropzone.dragover {
            border-color: var(--cyan);
            background: rgba(0, 229, 255, 0.05);
            box-shadow: 0 0 20px var(--cyan-glow);
        }

        .dropzone-icon {
            font-size: 40px;
            margin-bottom: 12px;
            display: block;
        }

        .btn {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
            background: var(--cyan);
            color: #03252b;
            font-weight: 700;
            border: none;
            padding: 10px 20px;
            border-radius: 12px;
            cursor: pointer;
            font-size: 14px;
            transition: transform 0.1s, filter 0.2s;
            text-decoration: none;
        }

        .btn:hover {
            filter: brightness(1.1);
        }

        .btn:active {
            transform: scale(0.98);
        }

        .btn-outline {
            background: transparent;
            color: var(--cyan);
            border: 1px solid var(--cyan);
        }

        .btn-outline:hover {
            background: rgba(0, 229, 255, 0.1);
        }

        .btn-sm {
            padding: 6px 14px;
            font-size: 13px;
            border-radius: 10px;
        }

        /* File List */
        .file-list {
            display: flex;
            flex-direction: column;
            gap: 10px;
            margin-top: 12px;
        }

        .file-item {
            background: var(--surface-variant);
            border: 1px solid var(--border);
            border-radius: 14px;
            padding: 12px 16px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 12px;
        }

        .file-info {
            display: flex;
            align-items: center;
            gap: 12px;
            overflow: hidden;
        }

        .file-icon {
            font-size: 24px;
            min-width: 32px;
            text-align: center;
        }

        .file-name {
            font-weight: 600;
            font-size: 14px;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            max-width: 400px;
        }

        .file-meta {
            font-size: 12px;
            color: var(--text-secondary);
        }

        .empty-placeholder {
            text-align: center;
            padding: 30px 16px;
            color: var(--text-secondary);
            font-size: 14px;
        }

        /* Progress List */
        .progress-list {
            margin-top: 16px;
            display: flex;
            flex-direction: column;
            gap: 12px;
        }

        .progress-item {
            background: #0f1627;
            border: 1px solid var(--border);
            border-radius: 12px;
            padding: 14px;
        }

        .progress-header {
            display: flex;
            justify-content: space-between;
            font-size: 13px;
            margin-bottom: 8px;
        }

        .progress-bar-bg {
            width: 100%;
            height: 8px;
            background: var(--surface-variant);
            border-radius: 4px;
            overflow: hidden;
        }

        .progress-bar-fill {
            height: 100%;
            background: linear-gradient(90deg, var(--cyan), var(--green));
            width: 0%;
            transition: width 0.15s ease;
        }

        .apk-banner {
            background: linear-gradient(135deg, rgba(0, 229, 255, 0.12) 0%, rgba(16, 185, 129, 0.12) 100%);
            border: 1px solid rgba(0, 229, 255, 0.3);
            border-radius: var(--card-radius);
            padding: 18px 22px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            flex-wrap: wrap;
            gap: 14px;
        }

        .footer {
            text-align: center;
            color: var(--text-secondary);
            font-size: 13px;
            padding: 16px;
        }

        @media (max-width: 600px) {
            .file-name { max-width: 180px; }
            header { flex-direction: column; align-items: flex-start; }
        }
    </style>
</head>
<body>
    <div class="container">
        <!-- Header -->
        <header>
            <div class="brand-section">
                <div class="logo-box">⚡</div>
                <div>
                    <h1 class="brand-title">BeamDrop Web Share</h1>
                    <p class="brand-sub">Transfer files between PC & Phone without internet</p>
                </div>
            </div>
            <div class="status-pill">
                <div class="status-dot"></div>
                <span id="device-pill">Connected to $safeDeviceName</span>
            </div>
        </header>

        <!-- P2P Transfer Lobby Banner -->
        <div style="background: linear-gradient(135deg, rgba(0, 229, 255, 0.1) 0%, rgba(16, 185, 129, 0.1) 100%); border: 1px solid rgba(0, 229, 255, 0.3); border-radius: var(--card-radius); padding: 18px 22px; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 14px;" id="lobbyBanner">
            <div style="display: flex; align-items: center; gap: 12px;">
                <span style="font-size: 26px;">🌐</span>
                <div>
                    <div style="font-weight: 800; font-size: 15px; color: #fff;" id="lobbyModeTitle">BeamDrop P2P Lobby: Active</div>
                    <div style="font-size: 13px; color: var(--text-secondary); margin-top: 2px;" id="lobbyModeSub">Synchronizing with phone • Swarm Mesh Ready</div>
                </div>
            </div>
            <div style="background: rgba(16, 185, 129, 0.15); border: 1px solid var(--green); color: var(--green); font-size: 13px; font-weight: 700; padding: 6px 14px; border-radius: 20px;" id="lobbyBadge">
                🟢 In Lobby (1-to-1)
            </div>
        </div>

        <!-- Sideload APK Banner -->
        <div class="apk-banner">
            <div>
                <h3 style="font-size: 15px; font-weight: 700; color: #fff;">Get BeamDrop on another phone</h3>
                <p style="font-size: 12px; color: var(--text-secondary); margin-top: 2px;">
                    Download the raw BeamDrop Android APK to share offline.
                </p>
            </div>
            <a href="/download/apk" class="btn btn-sm">
                📦 Download APK
            </a>
        </div>

        <!-- Section 1: Upload Files from PC to Phone -->
        <div class="card">
            <div class="card-header">
                <div class="card-title">
                    <span>📤</span> Upload to Phone
                </div>
                <span style="font-size: 12px; color: var(--text-secondary);">Files are saved to phone's Downloads</span>
            </div>

            <div class="dropzone" id="dropzone" onclick="document.getElementById('fileInput').click()">
                <span class="dropzone-icon">📁</span>
                <p style="font-weight: 600; font-size: 15px; margin-bottom: 4px;">
                    Drag & drop files here, or click to browse
                </p>
                <p style="font-size: 13px; color: var(--text-secondary);">
                    Any photos, videos, archives, or documents
                </p>
                <input type="file" id="fileInput" multiple style="display: none;" onchange="handleFileSelect(this.files)">
            </div>

            <div class="progress-list" id="progressList"></div>
        </div>

        <!-- Section 2: Download Files from Phone -->
        <div class="card">
            <div class="card-header">
                <div class="card-title">
                    <span>📥</span> Download from Phone
                </div>
                <button class="btn btn-outline btn-sm" onclick="fetchFiles()">
                    🔄 Refresh List
                </button>
            </div>

            <div id="fileListContainer" class="file-list">
                <div class="empty-placeholder">Loading available files from phone...</div>
            </div>
        </div>

        <div class="footer">
            🛡️ 100% Local Offline Transfer • Port 8080 • Direct Wi-Fi LAN
        </div>
    </div>

    <script>
        const dropzone = document.getElementById('dropzone');
        const fileListContainer = document.getElementById('fileListContainer');
        const progressList = document.getElementById('progressList');
        const devicePill = document.getElementById('device-pill');

        // Drag & drop handlers
        ['dragenter', 'dragover'].forEach(name => {
            dropzone.addEventListener(name, (e) => {
                e.preventDefault();
                e.stopPropagation();
                dropzone.classList.add('dragover');
            });
        });

        ['dragleave', 'drop'].forEach(name => {
            dropzone.addEventListener(name, (e) => {
                e.preventDefault();
                e.stopPropagation();
                dropzone.classList.remove('dragover');
            });
        });

        dropzone.addEventListener('drop', (e) => {
            const dt = e.dataTransfer;
            const files = dt.files;
            handleFileSelect(files);
        });

        function handleFileSelect(files) {
            if (!files || files.length === 0) return;
            for (let i = 0; i < files.length; i++) {
                uploadSingleFile(files[i]);
            }
        }

        function formatBytes(bytes) {
            if (bytes <= 0) return '0 B';
            const k = 1024;
            const sizes = ['B', 'KB', 'MB', 'GB'];
            const i = Math.floor(Math.log(bytes) / Math.log(k));
            return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
        }

        function getFileIcon(name, isApk) {
            if (isApk || name.endsWith('.apk')) return '📦';
            const ext = name.split('.').pop().toLowerCase();
            if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg'].includes(ext)) return '🖼️';
            if (['mp4', 'mkv', 'webm', 'mov', 'avi'].includes(ext)) return '🎬';
            if (['mp3', 'wav', 'ogg', 'm4a', 'flac'].includes(ext)) return '🎵';
            if (['pdf', 'doc', 'docx', 'txt', 'epub'].includes(ext)) return '📄';
            if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) return '🗜️';
            return '📁';
        }

        function uploadSingleFile(file) {
            const itemId = 'upload_' + Math.random().toString(36).substring(2, 9);
            const itemHtml = `
                <div class="progress-item" id="${'$'}{itemId}">
                    <div class="progress-header">
                        <span style="font-weight: 600;">${'$'}{escapeHtml(file.name)} (${'$'}{formatBytes(file.size)})</span>
                        <span id="${'$'}{itemId}_status" style="color: var(--cyan);">0%</span>
                    </div>
                    <div class="progress-bar-bg">
                        <div class="progress-bar-fill" id="${'$'}{itemId}_bar"></div>
                    </div>
                </div>
            `;
            progressList.insertAdjacentHTML('afterbegin', itemHtml);

            const xhr = new XMLHttpRequest();
            const statusBar = document.getElementById(itemId + '_bar');
            const statusText = document.getElementById(itemId + '_status');
            const startTime = Date.now();

            xhr.upload.addEventListener('progress', (e) => {
                if (e.lengthComputable) {
                    const percent = Math.round((e.loaded / e.total) * 100);
                    statusBar.style.width = percent + '%';
                    
                    const elapsed = (Date.now() - startTime) / 1000;
                    const speed = elapsed > 0 ? (e.loaded / elapsed) : 0;
                    statusText.innerText = percent + '% (' + formatBytes(speed) + '/s)';
                }
            });

            xhr.onreadystatechange = function () {
                if (xhr.readyState === 4) {
                    if (xhr.status === 200) {
                        statusBar.style.width = '100%';
                        statusBar.style.background = 'var(--green)';
                        statusText.style.color = 'var(--green)';
                        statusText.innerText = 'Completed ✅';
                        setTimeout(fetchFiles, 1000);
                    } else {
                        statusText.style.color = '#ef4444';
                        statusText.innerText = 'Failed ❌ (' + xhr.status + ')';
                    }
                }
            };

            xhr.open('POST', '/upload', true);
            xhr.setRequestHeader('x-file-name', encodeURIComponent(file.name));
            xhr.setRequestHeader('x-file-size', file.size.toString());
            xhr.setRequestHeader('x-sender-name', 'PC Web Browser');
            xhr.setRequestHeader('Content-Type', 'application/octet-stream');
            xhr.send(file);
        }

        async function fetchFiles() {
            try {
                const res = await fetch('/api/files');
                if (!res.ok) throw new Error('HTTP ' + res.status);
                const data = await res.json();
                
                if (data.deviceName) {
                    devicePill.innerText = 'Connected to ' + data.deviceName;
                }

                renderFiles(data.files || []);
            } catch (err) {
                fileListContainer.innerHTML = '<div class="empty-placeholder">Cannot connect to phone server. Ensure phone is on same Wi-Fi.</div>';
            }
        }

        function renderFiles(files) {
            if (!files || files.length === 0) {
                fileListContainer.innerHTML = `
                    <div class="empty-placeholder">
                        <p style="font-weight: 600; margin-bottom: 4px;">No files currently shared for download</p>
                        <p style="font-size: 13px;">Select items in the BeamDrop Android app to make them downloadable here, or drag & drop files above to send to the phone.</p>
                    </div>
                `;
                return;
            }

            fileListContainer.innerHTML = files.map(file => `
                <div class="file-item">
                    <div class="file-info">
                        <span class="file-icon">${'$'}{getFileIcon(file.name, file.isApk)}</span>
                        <div>
                            <div class="file-name" title="${'$'}{escapeHtml(file.name)}">${'$'}{escapeHtml(file.name)}</div>
                            <div class="file-meta">${'$'}{file.formattedSize || formatBytes(file.size)}</div>
                        </div>
                    </div>
                    <a href="/download?id=${'$'}{encodeURIComponent(file.id)}" class="btn btn-sm" download="${'$'}{escapeHtml(file.name)}">
                        ⬇️ Download
                    </a>
                </div>
            `).join('');
        }

        function escapeHtml(str) {
            if (!str) return '';
            return String(str)
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;')
                .replace(/"/g, '&quot;')
                .replace(/'/g, '&#039;');
        }

        async function pingLobby() {
            try {
                const res = await fetch('/api/lobby/ping', { method: 'POST' });
                if (res.ok) {
                    const data = await res.json();
                    const titleEl = document.getElementById('lobbyModeTitle');
                    const subEl = document.getElementById('lobbyModeSub');
                    const badgeEl = document.getElementById('lobbyBadge');
                    if (data.topology === 'SWARM' || (data.lobbyCount && data.lobbyCount >= 2)) {
                        titleEl.innerText = '🔥 P2P Swarm Mesh Active (' + data.lobbyCount + ' Devices)';
                        subEl.innerText = 'Multi-Peer mesh enabled • Direct peer forwarding ready';
                        badgeEl.innerText = '⚡ Swarm Ready';
                        badgeEl.style.color = 'var(--cyan)';
                        badgeEl.style.borderColor = 'var(--cyan)';
                    } else {
                        titleEl.innerText = '⚡ 1-to-1 Direct Transfer Lobby';
                        subEl.innerText = 'Direct ultra-fast stream • Connected to ' + (data.deviceName || 'Phone');
                        badgeEl.innerText = '🟢 In Lobby (1-to-1)';
                        badgeEl.style.color = 'var(--green)';
                        badgeEl.style.borderColor = 'var(--green)';
                    }
                }
            } catch (err) {
                console.warn('Lobby ping error', err);
            }
        }

        // Auto fetch on load and periodically
        fetchFiles();
        setInterval(fetchFiles, 4000);
        pingLobby();
        setInterval(pingLobby, 3500);
    </script>
</body>
</html>
        """.trimIndent()
    }
}
