<?php
require_once __DIR__ . '/config.php';
require_once __DIR__ . '/vendor/magnussolution/magnusbilling-api/src/magnusBilling.php';

use magnusbilling\api\MagnusBilling;

$magnus = new MagnusBilling(MAGNUS_API_KEY, MAGNUS_API_SECRET);
$magnus->public_url = MAGNUS_BASE_URL;

header('Content-Type: text/plain');

echo "--- MAGNUS API CDR DIAGNOSTIC ---\n\n";

try {
    // Try to read one call record to see the fields
    $res = $magnus->read('call', 1);
    if (isset($res['rows']) && !empty($res['rows'])) {
        echo "Found " . count($res['rows']) . " call records.\n";
        echo "Columns in the 'call' table:\n";
        print_r(array_keys($res['rows'][0]));
        echo "\nExample Call Record:\n";
        print_r($res['rows'][0]);
    } else {
        echo "No call records found in 'call' module.\n";
        print_r($res);
    }
} catch (Exception $e) {
    echo "ERROR: " . $e->getMessage() . "\n";
}
