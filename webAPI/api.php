<?php
ini_set('display_errors', 1);
ini_set('display_startup_errors', 1);
error_reporting(E_ALL);

// Load configuration
$configFile = __DIR__ . '/config.php';
if (!file_exists($configFile)) {
    header('Content-Type: application/json');
    die(json_encode(['status' => 'error', 'message' => "Configuration file not found. Please copy config.sample.php to config.php"]));
}
require_once $configFile;

$libFile = __DIR__ . '/vendor/magnussolution/magnusbilling-api/src/magnusBilling.php';
if (!file_exists($libFile)) {
    header('Content-Type: application/json');
    die(json_encode(['status' => 'error', 'message' => "Library file not found at: $libFile"]));
}
require_once $libFile;

use magnusbilling\api\MagnusBilling;

header('Content-Type: application/json');

$action = $_GET['action'] ?? 'balance';

try {
    $magnus = new MagnusBilling(MAGNUS_API_KEY, MAGNUS_API_SECRET);
    $magnus->public_url = MAGNUS_BASE_URL;

    switch ($action) {
        case 'login':
            $username = $_POST['username'] ?? '';
            $password = $_POST['password'] ?? '';

            if (empty($username) || empty($password)) {
                die(json_encode(['status' => 'error', 'message' => 'Username and password required']));
            }

            $magnus->clearFilter();
            $magnus->setFilter('username', $username, 'eq');
            $result = $magnus->read('user');

            if (!empty($result['rows'])) {
                $user = $result['rows'][0];
                // In MagnusBilling, the password might be plain text or md5 in the database
                // depending on configuration. We'll check against the returned 'password' field.
                if ($user['password'] === $password || $user['password'] === md5($password)) {
                    echo json_encode([
                        'status' => 'success',
                        'message' => 'Login successful',
                        'data' => [
                            'username' => $user['username'],
                            'firstname' => $user['firstname'],
                            'lastname' => $user['lastname'],
                            'credit' => $user['credit']
                        ]
                    ]);
                } else {
                    echo json_encode(['status' => 'error', 'message' => 'Invalid password']);
                }
            } else {
                echo json_encode(['status' => 'error', 'message' => 'User not found']);
            }
            break;

        case 'register':
            $username = $_POST['username'] ?? '';
            $password = $_POST['password'] ?? '';
            $firstname = $_POST['firstname'] ?? '';
            $lastname = $_POST['lastname'] ?? '';
            $email = $_POST['email'] ?? '';

            if (empty($username) || empty($password)) {
                die(json_encode(['status' => 'error', 'message' => 'Username and password required']));
            }

            $userData = [
                'username' => $username,
                'password' => $password,
                'firstname' => $firstname,
                'lastname' => $lastname,
                'email' => $email,
                'id_group' => 1,
                'id_plan' => 1,
                'active' => 1
            ];

            $result = $magnus->createUser($userData);

            if (isset($result['success']) && $result['success']) {
                echo json_encode([
                    'status' => 'success',
                    'message' => 'Registration successful',
                    'data' => $result
                ]);
            } else {
                echo json_encode([
                    'status' => 'error',
                    'message' => $result['message'] ?? 'Registration failed'
                ]);
            }
            break;

        case 'balance':
            $username = $_GET['username'] ?? null;
            if (!$username) die(json_encode(['status' => 'error', 'message' => 'Username required']));

            $magnus->setFilter('username', $username, 'eq');
            $result = $magnus->read('user');
            if (!empty($result['rows'])) {
                echo json_encode([
                    'status'   => 'success',
                    'balance'  => $result['rows'][0]['credit']
                ]);
            } else {
                echo json_encode(['status' => 'error', 'message' => 'User not found']);
            }
            break;

        case 'cdr':
            $username = $_GET['username'] ?? null;
            if (!$username) die(json_encode(['status' => 'error', 'message' => 'Username required']));

            $magnus->setFilter('username', $username, 'eq');
            $result = $magnus->read('cdr');
            echo json_encode([
                'status' => 'success',
                'data' => $result['rows'] ?? []
            ]);
            break;

        default:
            echo json_encode(['status' => 'error', 'message' => 'Invalid action']);
            break;
    }

} catch (Exception $e) {
    echo json_encode([
        'status' => 'error',
        'message' => $e->getMessage()
    ]);
}
