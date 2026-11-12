<?php
/**
 * Router for PHP's built-in web server.
 *
 * Usage:
 *   TOGETL_DB=out/togetl.sqlite3 php -S localhost:8080 web/router.php
 */

$path = parse_url(
    isset($_SERVER['REQUEST_URI']) ? $_SERVER['REQUEST_URI'] : '/',
    PHP_URL_PATH
);
$file = __DIR__ . $path;
if ($path !== '/' && $path !== '/index.php' && is_file($file)) {
    return false;
}
require __DIR__ . '/index.php';
