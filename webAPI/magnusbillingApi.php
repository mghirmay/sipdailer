<?php
/**
 * Optimized MagnusBilling API Gateway
 */

// Production settings
error_reporting(0);
ini_set('display_errors', 0);

header('Content-Type: application/json');
header('X-Content-Type-Options: nosniff');

// 1. Requirements Check
$configFile = __DIR__ . '/config.php';
$libFile = __DIR__ . '/vendor/magnussolution/magnusbilling-api/src/magnusBilling.php';

if (!file_exists($configFile) || !file_exists($libFile)) {
    http_response_code(500);
    die(json_encode(['status' => 'error', 'message' => 'System configuration error']));
}

require_once $configFile;
require_once $libFile;

use magnusbilling\api\MagnusBilling;

// 2. Helper for JSON responses
function sendResponse($status, $payload = []) {
    $payload['status'] = $status;
    echo json_encode($payload);
    exit;
}

// 3. Input Sanitization
$action = filter_input(INPUT_GET, 'action', FILTER_SANITIZE_STRING) ?: 'balance';
$username = filter_input(INPUT_GET, 'username', FILTER_SANITIZE_STRING) ?:
            filter_input(INPUT_POST, 'username', FILTER_SANITIZE_STRING);

try {
    $magnus = new MagnusBilling(MAGNUS_API_KEY, MAGNUS_API_SECRET);
    $magnus->public_url = MAGNUS_BASE_URL;

    switch ($action) {
        case 'login':
            $password = $_POST['password'] ?? '';
            if (empty($username) || empty($password)) {
                sendResponse('error', ['message' => 'Credentials required']);
            }

            $magnus->setFilter('username', $username, 'eq');
            $result = $magnus->read('user');

            if (!empty($result['rows'])) {
                $user = $result['rows'][0];
                // Check plain text or md5
                if ($user['password'] === $password || $user['password'] === md5($password)) {
                    sendResponse('success', [
                        'message' => 'Login successful',
                        'data' => [
                            'username' => $user['username'],
                            'firstname' => $user['firstname'],
                            'lastname' => $user['lastname'],
                            'credit' => $user['credit']
                        ]
                    ]);
                }
            }
            sendResponse('error', ['message' => 'Invalid username or password']);
            break;

        case 'register':
            $password = $_POST['password'] ?? '';
            if (empty($username) || empty($password)) {
                sendResponse('error', ['message' => 'Username and password required']);
            }

            $userData = [
                'username' => $username,
                'password' => $password,
                'firstname' => $_POST['firstname'] ?? '',
                'lastname' => $_POST['lastname'] ?? '',
                'email' => $_POST['email'] ?? '',
                'id_group' => 1,
                'id_plan' => 1,
                'active' => 1
            ];

            $result = $magnus->createUser($userData);

            if (isset($result['success']) && $result['success']) {
                sendResponse('success', ['message' => 'Registration successful', 'data' => $result]);
            } else {
                sendResponse('error', ['message' => $result['message'] ?? 'Registration failed']);
            }
            break;

        case 'balance':
            if (!$username) sendResponse('error', ['message' => 'Username required']);

            $magnus->setFilter('username', $username, 'eq');
            // Optimization: Only fetch the specific user record
            $result = $magnus->read('user');

            if (!empty($result['rows'])) {
                sendResponse('success', ['balance' => $result['rows'][0]['credit']]);
            } else {
                sendResponse('error', ['message' => 'User not found']);
            }
            break;

        case 'cdr':
            if (!$username) sendResponse('error', ['message' => 'Username required']);

            $magnus->setFilter('username', $username, 'eq');
            // MagnusBilling SDK read() method usually returns a limited set by default.
            // We ensure we get the history for this user specifically.
            $result = $magnus->read('cdr');

            sendResponse('success', ['data' => $result['rows'] ?? []]);
            break;

        default:
            sendResponse('error', ['message' => 'Invalid action']);
            break;
    }

} catch (Exception $e) {
    sendResponse('error', ['message' => 'API Error: ' . $e->getMessage()]);
}
