<?php
/**
 * Shared helpers for the read-only TOGETL browser.
 *
 * The database is opened with PRAGMA query_only so no page can modify it.
 */

function togetl_db_path()
{
    $env = getenv('TOGETL_DB');
    if ($env !== false && $env !== '') {
        return $env;
    }
    return dirname(__DIR__) . '/out/togetl.sqlite3';
}

function db()
{
    static $pdo = null;
    if ($pdo !== null) {
        return $pdo;
    }
    $path = togetl_db_path();
    if (!is_file($path)) {
        throw new RuntimeException(
            'Database not found: ' . $path . ". Run 'togetl load ...' first."
        );
    }
    $pdo = new PDO('sqlite:' . $path);
    $pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
    $pdo->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
    $pdo->exec('PRAGMA query_only = ON');
    return $pdo;
}

function h($value)
{
    return htmlspecialchars((string) $value, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

function url($page, array $params = array())
{
    $params = array_merge(array('page' => $page), $params);
    return 'index.php?' . http_build_query($params, '', '&', PHP_QUERY_RFC3986);
}

function subject_url($iri)
{
    return url('subject', array('iri' => $iri));
}

function predicate_url($iri)
{
    return url('predicate', array('iri' => $iri));
}

function document_url($iri)
{
    return url('document', array('iri' => $iri));
}

function part_url($iri)
{
    return url('part', array('iri' => $iri));
}

function resource_url($iri)
{
    if (strpos($iri, 'tag:nuke24.net,2026-11:togetl:part:') === 0) {
        return part_url($iri);
    }
    if (strpos($iri, 'tag:nuke24.net,2026-11:togetl:document:') === 0) {
        return document_url($iri);
    }
    return subject_url($iri);
}

function resource_link($iri)
{
    return '<a href="' . h(resource_url($iri)) . '">' . h(short_iri($iri)) . '</a>';
}

function fetch_all($sql, array $params = array())
{
    $statement = db()->prepare($sql);
    $statement->execute($params);
    return $statement->fetchAll();
}

function fetch_one($sql, array $params = array())
{
    $statement = db()->prepare($sql);
    $statement->execute($params);
    $row = $statement->fetch();
    return $row === false ? null : $row;
}

function scalar($sql, array $params = array())
{
    $statement = db()->prepare($sql);
    $statement->execute($params);
    return $statement->fetchColumn();
}

function short_iri($iri)
{
    $prefixes = array(
        'http://ns.nuke24.net/TOGETL/' => 'togetl:',
        'http://ns.nuke24.net/TOGETL/TEF/' => 'tef:',
        'http://www.w3.org/1999/02/22-rdf-syntax-ns#' => 'rdf:',
        'http://www.w3.org/2000/01/rdf-schema#' => 'rdfs:',
        'http://www.w3.org/2001/XMLSchema#' => 'xsd:',
        'tag:nuke24.net,2026-11:togetl:' => 'togetl:',
    );
    foreach ($prefixes as $prefix => $short) {
        if (strpos($iri, $prefix) === 0) {
            return $short . substr($iri, strlen($prefix));
        }
    }
    return $iri;
}

function location_text(array $row)
{
    if ($row['line'] === null || $row['column'] === null) {
        return '-';
    }
    $text = 'L' . $row['line'] . 'C' . $row['column'];
    if ($row['end_line'] !== null && $row['end_column'] !== null) {
        $text .= '...L' . $row['end_line'] . 'C' . $row['end_column'];
    }
    return $text;
}

function render_value($object, $kind, $datatype = null)
{
    if ($kind === 'iri') {
        return '<a class="iri" href="' . h(resource_url($object)) . '">'
            . h(short_iri($object)) . '</a>';
    }
    if ($kind === 'blank') {
        return '<span class="blank">' . h($object) . '</span>';
    }
    $text = '<span class="literal">' . h($object) . '</span>';
    if ($datatype !== null && $datatype !== '') {
        $text .= ' <span class="datatype">(' . h(short_iri($datatype)) . ')</span>';
    }
    return $text;
}

function render_header($title)
{
    ?><!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<title><?= h($title) ?> — TOGETL</title>
<style>
body { font-family: system-ui, sans-serif; margin: 0; color: #222; }
header { background: #263238; color: #fff; padding: 0.6rem 1rem; }
header a { color: #fff; margin-right: 1rem; text-decoration: none; }
main { padding: 1rem; max-width: 1200px; }
h1 { font-size: 1.4rem; margin-top: 0; }
table { border-collapse: collapse; width: 100%; margin: 0.5rem 0 1.5rem; }
th, td { border-bottom: 1px solid #ddd; padding: 0.25rem 0.4rem; text-align: left; vertical-align: top; }
th { background: #f5f5f5; }
code, .iri, .literal, .blank { font-family: ui-monospace, monospace; font-size: 0.9em; }
.literal { white-space: pre-wrap; }
.datatype { color: #777; font-size: 0.8em; }
.muted { color: #777; }
.error { background: #ffebee; border: 1px solid #ef9a9a; padding: 0.75rem; }
nav { margin-bottom: 1rem; }
</style>
</head>
<body>
<header>
<strong>TOGETL</strong>
<a href="<?= h(url('home')) ?>">Home</a>
<a href="<?= h(url('vocab')) ?>">Vocabulary</a>
<a href="<?= h(url('issues')) ?>">Issues</a>
</header>
<main>
<h1><?= h($title) ?></h1>
<?php
}

function render_footer()
{
    ?></main>
</body>
</html><?php
}

function render_error($message)
{
    echo '<div class="error">' . h($message) . '</div>';
}

function require_iri($name)
{
    $value = isset($_GET[$name]) ? trim((string) $_GET[$name]) : '';
    if ($value === '') {
        throw new InvalidArgumentException('Missing ' . $name . ' parameter');
    }
    return $value;
}
