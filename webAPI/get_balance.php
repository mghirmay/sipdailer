<?php
ini_set('display_errors', 1);
ini_set('display_startup_errors', 1);
error_reporting(E_ALL);

$libFile = __DIR__ . '/vendor/magnussolution/magnusbilling-api/src/magnusBilling.php';
if (!file_exists($libFile)) {
    header('Content-Type: application/json');
    die(json_encode(['status' => 'error', 'message' => "Library file not found at: $libFile"]));
}
require_once $libFile;

use magnusbilling\api\MagnusBilling;

header('Content-Type: application/json');

try {

    // --- CONFIGURATION ---
    $apiKey    = 'YOUR_API_KEY';
    $apiSecret = 'YOUR_API_SECRET'; // <--- REPLACE THIS WITH YOUR REAL SECRET
    $baseUrl   = 'https://sinitpower.de/mbilling';

    // --- INPUT ---
    $userToCheck = $_GET['username'] ?? null;

    if (!$userToCheck) {
        die(json_encode(['status' => 'error', 'message' => 'Username is required']));
    }

    // --- API LOGIC ---
    // Note: The class name inside the file is MagnusBilling (Uppercase M)
    $magnus = new MagnusBilling($apiKey, $apiSecret);
    $magnus->public_url = $baseUrl;

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
