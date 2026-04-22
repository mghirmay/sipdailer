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

try {
    // --- INPUT ---
    $userToCheck = $_GET['username'] ?? null;

    if (!$userToCheck) {
        die(json_encode(['status' => 'error', 'message' => 'Username is required']));
    }

    // --- API LOGIC ---
    $magnus = new MagnusBilling(MAGNUS_API_KEY, MAGNUS_API_SECRET);
    $magnus->public_url = MAGNUS_BASE_URL;

    $magnus->clearFilter();
    $magnus->setFilter('username', $userToCheck, 'eq');

    $result = $magnus->read('user');

    if (!empty($result['rows'])) {
        $user = $result['rows'][0];
        echo json_encode([
            'status'   => 'success',
            'balance'  => $user['credit']
        ]);
    } else {
        echo json_encode([
            'status' => 'error',
            'message' => 'User not found or API error',
            'details' => $result
        ]);
    }

} catch (Exception $e) {
    echo json_encode([
        'status' => 'error',
        'message' => $e->getMessage()
    ]);
}
