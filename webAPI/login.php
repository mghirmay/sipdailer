<?php
/**
 * SinitPower Web Portal - Fixed Login & Added History
 */

$configFile = __DIR__ . '/config.php';
if (!file_exists($configFile)) {
    define('PAYPAL_NAME', 'sinitpower@gmail.com');
} else {
    require_once $configFile;
}

$paypal_id = defined('PAYPAL_NAME') ? PAYPAL_NAME : 'sinitpower@gmail.com';
$is_email = strpos($paypal_id, '@') !== false;
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>SinitPower - User Portal</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.1/font/bootstrap-icons.css">
    <style>
        body { background-color: #f1f4f9; font-family: 'Inter', system-ui, sans-serif; }
        .card { border: none; border-radius: 16px; box-shadow: 0 4px 24px rgba(0,0,0,0.06); height: 100%; }
        .hidden { display: none; }
        .credential-box { background-color: #f8f9fa; border-radius: 12px; padding: 1.5rem; }
        .label-muted { color: #8e9aaf; font-size: 0.7rem; text-transform: uppercase; font-weight: 700; letter-spacing: 0.5px; }
        .value-text { font-weight: 600; color: #1a1d23; display: block; }
        .status-dot { height: 10px; width: 10px; border-radius: 50%; display: inline-block; margin-right: 6px; }
        .dot-online { background-color: #10b981; box-shadow: 0 0 8px rgba(16,185,129,0.4); }
        .dot-offline { background-color: #ef4444; }
        .paypal-box { background: linear-gradient(135deg, #003087 0%, #0070ba 100%); color: white; border-radius: 12px; padding: 2rem; }
        .qr-placeholder { background: white; width: 150px; height: 150px; margin: 0 auto; border-radius: 8px; padding: 10px; }
        .history-table { font-size: 0.85rem; }
        .logout-btn { position: absolute; bottom: 20px; left: 40px; }
        .val-small { font-size: 1.1rem !important; }
    </style>
</head>
<body>

<div class="container py-5">
    <div class="row justify-content-center">
        <div id="mainCol" class="col-md-6 col-lg-5 transition-all">
            <div class="text-center mb-4">
                <h2 class="text-primary fw-bold">SinitPower</h2>
                <p class="text-secondary small">Secure SIP & Billing Dashboard</p>
            </div>

            <!-- LOGIN CARD -->
            <div id="loginCard" class="card p-4">
                <h5 class="mb-4 text-center fw-bold">Sign In</h5>
                <form id="loginForm">
                    <div class="mb-3">
                        <label class="form-label small fw-bold">USERNAME</label>
                        <input type="text" id="username" class="form-control" required placeholder="name@domain.com">
                    </div>
                    <div class="mb-3">
                        <label class="form-label small fw-bold">PASSWORD</label>
                        <input type="password" id="password" class="form-control" required placeholder="••••••••">
                    </div>
                    <div class="mb-3 form-check">
                        <input type="checkbox" class="form-check-input" id="rememberMe">
                        <label class="form-check-label small text-secondary" for="rememberMe">Remember me</label>
                    </div>
                    <button type="submit" id="loginBtn" class="btn btn-primary w-100 py-2 fw-bold">Sign In</button>
                </form>
            </div>

            <!-- DASHBOARD CARD -->
            <div id="dashboardCard" class="card p-5 hidden" style="min-height: 600px;">
                <div class="row g-5">
                    <!-- LEFT COLUMN -->
                    <div class="col-md-7 border-end">
                        <div class="d-flex justify-content-between align-items-center mb-4">
                            <div>
                                <h3 class="mb-0 fw-bold" id="displayName">---</h3>
                                <span class="text-muted" id="displayEmail">---</span>
                            </div>
                            <div class="text-end">
                                <span class="badge bg-light text-dark border p-2 px-3 rounded-pill">
                                    <span class="status-dot" id="statusDot"></span> 
                                    <span id="statusText" class="fw-bold">---</span>
                                </span>
                            </div>
                        </div>

                        <div class="row g-3 mb-4 text-center">
                            <div class="col-6">
                                <div class="p-3 border rounded-3 bg-white shadow-sm">
                                    <span class="label-muted d-block mb-1">Balance</span>
                                    <h2 class="text-primary mb-0 fw-bold val-small">€<span id="displayBalance">0.00</span></h2>
                                </div>
                            </div>
                            <div class="col-6">
                                <div class="p-3 border rounded-3 bg-white shadow-sm">
                                    <span class="label-muted d-block mb-1">Account Code</span>
                                    <h2 class="text-dark mb-0 fw-bold val-small" id="displayAccountCode">---</h2>
                                </div>
                            </div>
                        </div>

                        <div class="credential-box mb-4">
                            <h6 class="label-muted mb-3 pb-2 border-bottom">Enhanced SIP Details</h6>
                            <div class="row g-3">
                                <div class="col-6"><span class="label-muted">Extension</span><span class="value-text" id="sipUser">---</span></div>
                                <div class="col-6"><span class="label-muted">Auth Name</span><span class="value-text" id="sipAuthName">---</span></div>
                                <div class="col-6"><span class="label-muted">Caller ID</span><span class="value-text" id="sipCallerId">---</span></div>
                                <div class="col-6"><span class="label-muted">Latency</span><span class="value-text text-success" id="latency">---</span></div>
                            </div>
                        </div>

                        <div id="historySection">
                            <h6 class="label-muted mb-3 pb-2 border-bottom">Recent Call History</h6>
                            <div class="table-responsive">
                                <table class="table table-sm history-table">
                                    <thead>
                                        <tr class="text-muted" style="font-size: 0.7rem;">
                                            <th>DATE</th>
                                            <th>DESTINATION</th>
                                            <th>DURATION</th>
                                            <th>COST</th>
                                        </tr>
                                    </thead>
                                    <tbody id="callHistoryBody">
                                        <!-- Calls injected here -->
                                    </tbody>
                                </table>
                            </div>
                        </div>
                        
                        <button onclick="handleLogout()" class="btn btn-link text-muted p-0 logout-btn text-decoration-none small">
                            <i class="bi bi-box-arrow-left me-1"></i> Sign Out
                        </button>
                    </div>

                    <!-- RIGHT COLUMN -->
                    <div class="col-md-5">
                        <div class="paypal-box text-center shadow">
                            <h5 class="fw-bold mb-4"><i class="bi bi-paypal me-2"></i> Fast Refill</h5>
                            <div class="qr-placeholder mb-4 shadow-sm">
                                <img id="paypalQR" src="" alt="PayPal QR" class="img-fluid">
                            </div>
                            <p class="mb-1">Scan to pay <span class="badge bg-warning text-dark fs-6 fw-bold">€<span id="selectedAmountLabel">25</span></span></p>
                            <p class="small mb-4 opacity-75">To: <b><?php echo $paypal_id; ?></b><br>via Friends & Family</p>
                            <a id="paypalBtn" href="#" target="_blank" class="btn btn-light w-100 fw-bold text-primary py-2 mb-4">Pay via PayPal</a>
                            <hr class="opacity-25 mb-4">
                            <form id="refillForm">
                                <div class="mb-3">
                                    <select id="refillAmount" class="form-select form-select-lg fw-bold" onchange="updateAmount(this.value)">
                                        <option value="25">€25.00</option><option value="30">€30.00</option><option value="40">€40.00</option><option value="50">€50.00</option><option value="100">€100.00</option>
                                    </select>
                                </div>
                                <button class="btn btn-warning btn-lg w-100 fw-bold shadow-sm" type="submit">Notify Admin</button>
                            </form>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>

<script>
let currentUser = '';
let currentPass = '';
const PAYPAL_NAME = '<?php echo $paypal_id; ?>';
const isEmail = <?php echo $is_email ? 'true' : 'false'; ?>;
let selectedAmount = 25;
let refreshInterval = null;

function updateAmount(amt) {
    selectedAmount = amt;
    document.getElementById('selectedAmountLabel').innerText = amt;
    updatePaypalLinks();
}

function updatePaypalLinks() {
    let url = isEmail ? `https://www.paypal.com/cgi-bin/webscr?cmd=_xclick&business=${encodeURIComponent(PAYPAL_NAME)}&currency_code=EUR&amount=${selectedAmount}&item_name=Refill+for+${currentUser}` : `https://www.paypal.me/${PAYPAL_NAME}/${selectedAmount}EUR`;
    document.getElementById('paypalBtn').href = url;
    document.getElementById('paypalQR').src = `https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=${encodeURIComponent(url)}`;
}

function refreshDashboard() {
    if (!currentUser || !currentPass) return;
    
    const formData = new FormData();
    formData.append('action', 'login');
    formData.append('username', currentUser);
    formData.append('password', currentPass);

    fetch('magnusbillingApi.php', { method: 'POST', body: formData })
    .then(r => r.json())
    .then(res => {
        if (res.status === 'success') {
            updateUI(res.data, res.calls);
        }
    });
}

function updateUI(data, calls) {
    document.getElementById('mainCol').className = 'col-md-11 col-lg-10';
    document.getElementById('loginCard').classList.add('hidden');
    document.getElementById('dashboardCard').classList.remove('hidden');
    
    document.getElementById('displayName').innerText = data.firstname + ' ' + data.lastname;
    document.getElementById('displayEmail').innerText = data.email;
    document.getElementById('displayBalance').innerText = parseFloat(data.credit).toFixed(2);
    document.getElementById('displayAccountCode').innerText = data.accountcode;
    document.getElementById('sipUser').innerText = data['sip-username'];
    document.getElementById('sipAuthName').innerText = data['auth-name'];
    document.getElementById('sipCallerId').innerText = data['callerid'];
    document.getElementById('latency').innerText = data.latency + ' ms';
    
    const dot = document.getElementById('statusDot');
    dot.className = "status-dot " + (data['sip-status'] === 'Registered' ? 'dot-online' : 'dot-offline');
    document.getElementById('statusText').innerText = data['sip-status'];

    const body = document.getElementById('callHistoryBody');
    body.innerHTML = '';
    if (calls) {
        calls.forEach(c => {
            body.innerHTML += `<tr>
                <td>${c.starttime.substring(5,16)}</td>
                <td>${c.calledstation}</td>
                <td>${Math.floor(c.sessiontime/60)}m ${c.sessiontime%60}s</td>
                <td class="fw-bold text-primary">€${parseFloat(c.sessionbill).toFixed(3)}</td>
            </tr>`;
        });
    }
    
    updatePaypalLinks();
    
    // Set up auto-refresh if not already running
    if (!refreshInterval) {
        refreshInterval = setInterval(refreshDashboard, 15000);
    }
}

window.addEventListener('load', () => {
    const savedUser = localStorage.getItem('sinit_username');
    const savedPass = localStorage.getItem('sinit_password');
    if (savedUser && savedPass) {
        document.getElementById('username').value = savedUser;
        document.getElementById('password').value = savedPass;
        document.getElementById('rememberMe').checked = true;
    }
});

document.getElementById('loginForm').onsubmit = function(e) {
    e.preventDefault();
    currentUser = document.getElementById('username').value;
    currentPass = document.getElementById('password').value;
    
    const formData = new FormData();
    formData.append('action', 'login');
    formData.append('username', currentUser);
    formData.append('password', currentPass);

    fetch('magnusbillingApi.php', { method: 'POST', body: formData })
    .then(r => r.json())
    .then(res => {
        if (res.status === 'success') {
            if (document.getElementById('rememberMe').checked) {
                localStorage.setItem('sinit_username', currentUser);
                localStorage.setItem('sinit_password', currentPass);
            }
            updateUI(res.data, res.calls);
        } else alert(res.message);
    });
};

document.getElementById('refillForm').onsubmit = function(e) {
    e.preventDefault();
    const amount = document.getElementById('refillAmount').value;
    const formData = new FormData();
    formData.append('action', 'manual_refill');
    formData.append('username', currentUser);
    formData.append('amount', amount);
    fetch('magnusbillingApi.php', { method: 'POST', body: formData })
    .then(r => r.json())
    .then(res => alert(res.message));
};

function handleLogout() { 
    if (refreshInterval) clearInterval(refreshInterval);
    location.reload(); 
}
</script>
</body>
</html>
