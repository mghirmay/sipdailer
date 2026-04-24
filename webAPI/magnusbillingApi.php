<?php
/**
 * MagnusBilling API Gateway - Enhanced for Android & Web
 */

ob_start();
error_reporting(E_ALL);
ini_set('display_errors', 0);

header('Content-Type: application/json');

$configFile = __DIR__ . '/config.php';
$libFile = __DIR__ . '/vendor/magnussolution/magnusbilling-api/src/magnusBilling.php';

if (!file_exists($configFile)) {
    ob_end_clean();
    die(json_encode(['status' => 'error', 'message' => 'Missing config.php']));
}

require_once $configFile;
require_once $libFile;

use magnusbilling\api\MagnusBilling;

function sendResponse($status, $payload = []) {
    $payload['status'] = $status;
    if (ob_get_length()) ob_end_clean();
    echo json_encode($payload);
    exit;
}

$action = $_REQUEST['action'] ?? 'balance';
$username = $_REQUEST['username'] ?? '';
$password = $_REQUEST['password'] ?? '';

try {
    $magnus = new MagnusBilling(MAGNUS_API_KEY, MAGNUS_API_SECRET);
    $magnus->public_url = MAGNUS_BASE_URL;

    // Common function to fetch user and SIP details
    function getFullUserData($magnus, $username) {
        $magnus->clearFilter();
        $magnus->setFilter('username', $username, 'eq');
        $result = $magnus->read('user');

        if (!isset($result['rows']) || empty($result['rows'])) {
            $magnus->clearFilter();
            $magnus->setFilter('email', $username, 'eq');
            $result = $magnus->read('user');
        }

        if (isset($result['rows']) && !empty($result['rows'])) {
            $user = $result['rows'][0];
            
            // Try to find SIP account using multiple strategies
            $sipResult = null;
            
            // 1. By id_user
            $magnus->clearFilter();
            $magnus->setFilter('id_user', $user['id'], 'eq');
            $res = $magnus->read('sip');
            if (isset($res['rows']) && !empty($res['rows'])) {
                $sipResult = $res;
            } else {
                // 2. By name matching username
                $magnus->clearFilter();
                $magnus->setFilter('name', $user['username'], 'eq');
                $res = $magnus->read('sip');
                if (isset($res['rows']) && !empty($res['rows'])) {
                    $sipResult = $res;
                } else {
                    // 3. By accountcode matching username
                    $magnus->clearFilter();
                    $magnus->setFilter('accountcode', $user['username'], 'eq');
                    $res = $magnus->read('sip');
                    if (isset($res['rows']) && !empty($res['rows'])) {
                        $sipResult = $res;
                    }
                }
            }
            
            $sipDetails = [
                'name' => $user['username'],
                'callerid' => ($user['firstname'] ?? '') . ' ' . ($user['lastname'] ?? ''),
                'accountcode' => $user['accountcode'] ?? 'N/A',
                'useragent' => 'None',
                'lastms' => '0',
                'fullcontact' => '',
                'ipaddr' => '',
                'lineStatus' => 'UNKNOWN',
                'regseconds' => 0,
                'secret' => '',
                'isRegistered' => false
            ];
            
            if ($sipResult && isset($sipResult['rows']) && !empty($sipResult['rows'])) {
                $bestSip = null;
                $isRegistered = false;

                foreach ($sipResult['rows'] as $s) {
                    $statusStr = strtoupper($s['lineStatus'] ?? '');
                    $regSecs = (int)($s['regseconds'] ?? 0);
                    $ip = $s['ipaddr'] ?? '';
                    $ms = (int)($s['lastms'] ?? 0);
                    
                    $registered = (
                        strpos($statusStr, 'OK') !== false || 
                        strpos($statusStr, 'LAGGED') !== false ||
                        !empty($s['fullcontact']) || 
                        (!empty($ip) && $ip !== '0.0.0.0') ||
                        $ms > 0 ||
                        $regSecs > time()
                    );
                    
                    if ($registered || $bestSip === null) {
                        $bestSip = $s;
                        $isRegistered = $registered;
                        if ($isRegistered) break; // Use the first registered one found
                    }
                }

                if ($bestSip) {
                    $sipDetails['name'] = $bestSip['name'] ?? $sipDetails['name'];
                    $sipDetails['callerid'] = $bestSip['callerid'] ?? $sipDetails['callerid'];
                    $sipDetails['accountcode'] = $bestSip['accountcode'] ?? $sipDetails['accountcode'];
                    $sipDetails['useragent'] = $bestSip['useragent'] ?? 'Inactive';
                    $sipDetails['lastms'] = $bestSip['lastms'] ?? '0';
                    $sipDetails['fullcontact'] = $bestSip['fullcontact'] ?? '';
                    $sipDetails['ipaddr'] = $bestSip['ipaddr'] ?? '';
                    $sipDetails['lineStatus'] = $bestSip['lineStatus'] ?? 'UNKNOWN';
                    $sipDetails['regseconds'] = $bestSip['regseconds'] ?? 0;
                    $sipDetails['secret'] = $bestSip['secret'] ?? $bestSip['sippasswd'] ?? '';
                    $sipDetails['isRegistered'] = $isRegistered;
                }
            }

            return [
                'user' => $user,
                'sip' => $sipDetails
            ];
        }
        return null;
    }

    switch ($action) {
        case 'login':
            if (empty($username) || empty($password)) sendResponse('error', ['message' => 'Credentials required']);
            
            $fullData = getFullUserData($magnus, $username);
            if ($fullData) {
                $user = $fullData['user'];
                $sipDetails = $fullData['sip'];
                
                $isMatch = ($user['password'] === $password || $user['password'] === md5($password));
                
                if ($isMatch) {
                    // Fetch Call History
                    $calls = [];
                    try {
                        $magnus->clearFilter();
                        $magnus->setFilter('id_user', $user['id'], 'eq');
                        $callRes = $magnus->read('call');
                        $calls = $callRes['rows'] ?? [];
                        usort($calls, function($a, $b) {
                            return strtotime($b['starttime'] ?? 0) - strtotime($a['starttime'] ?? 0);
                        });
                        $calls = array_slice($calls, 0, 10);
                    } catch (Exception $e) {}

                    sendResponse('success', [
                        'data' => [
                            'firstname' => $user['firstname'] ?? 'User',
                            'lastname' => $user['lastname'] ?? '',
                            'email' => $user['email'] ?? '',
                            'credit' => $user['credit'] ?? '0.00',
                            'accountcode' => $sipDetails['accountcode'],
                            'sip-username' => $sipDetails['name'],
                            'sip-password' => $sipDetails['secret'],
                            'auth-name' => $sipDetails['name'],
                            'useragent' => $sipDetails['useragent'],
                            'latency' => $sipDetails['lastms'],
                            'sip-status' => $sipDetails['isRegistered'] ? 'Registered' : 'Offline',
                            'callerid' => $sipDetails['callerid']
                        ],
                        'calls' => $calls
                    ]);
                }
            }
            sendResponse('error', ['message' => 'Invalid credentials']);
            break;

        case 'user_info':
            $fullData = getFullUserData($magnus, $username);
            if ($fullData) {
                $user = $fullData['user'];
                $sipDetails = $fullData['sip'];
                
                sendResponse('success', [
                    'data' => [
                        'firstname' => $user['firstname'],
                        'lastname' => $user['lastname'],
                        'email' => $user['email'],
                        'credit' => $user['credit'],
                        'accountcode' => $sipDetails['accountcode'],
                        'sip-username' => $sipDetails['name'],
                        'sip-password' => $sipDetails['secret'],
                        'callerid' => $sipDetails['callerid'],
                        'sip-status' => $sipDetails['isRegistered'] ? 'Registered' : 'Offline'
                    ]
                ]);
            }
            sendResponse('error', ['message' => 'User not found']);
            break;

        case 'call': // Specific for Android history refresh
            $fullData = getFullUserData($magnus, $username);
            if ($fullData) {
                $user = $fullData['user'];
                $magnus->clearFilter();
                $magnus->setFilter('id_user', $user['id'], 'eq');
                $callRes = $magnus->read('call');
                $calls = $callRes['rows'] ?? [];
                usort($calls, function($a, $b) {
                    return strtotime($b['starttime'] ?? 0) - strtotime($a['starttime'] ?? 0);
                });
                sendResponse('success', ['data' => array_slice($calls, 0, 20)]);
            }
            sendResponse('error', ['message' => 'User not found']);
            break;

        case 'balance':
            $magnus->setFilter('username', $username, 'eq');
            $result = $magnus->read('user');
            if (isset($result['rows'][0])) {
                sendResponse('success', ['balance' => $result['rows'][0]['credit']]);
            }
            sendResponse('error', ['message' => 'User not found']);
            break;

        case 'manual_refill':
            $amount = $_REQUEST['amount'] ?? 0;
            if ($amount < 25) sendResponse('error', ['message' => 'Minimum refill is 25 Euro']);
            $magnus->setFilter('username', $username, 'eq');
            $userRes = $magnus->read('user');
            if (isset($userRes['rows'][0])) {
                $magnus->create('refill', [
                    'id_user' => $userRes['rows'][0]['id'],
                    'credit' => $amount,
                    'description' => "PAYPAL PENDING",
                    'payment_method' => 'PayPal QR'
                ]);
                sendResponse('success', ['message' => 'Notification sent.']);
            }
            sendResponse('error', ['message' => 'User not found']);
            break;
    }
} catch (Exception $e) {
    sendResponse('error', ['message' => $e->getMessage()]);
}
