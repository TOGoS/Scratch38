<?php
/**
 * Read-only TOGETL browser front controller.
 *
 * Pages can be reached either as index.php?page=subject&iri=... or as
 * /subject?iri=... when the server routes requests through this file.
 */

require __DIR__ . '/lib.php';

$page = isset($_GET['page']) ? (string) $_GET['page'] : '';
if ($page === '') {
    $requestPath = parse_url(
        isset($_SERVER['REQUEST_URI']) ? $_SERVER['REQUEST_URI'] : '/',
        PHP_URL_PATH
    );
    $scriptName = isset($_SERVER['SCRIPT_NAME']) ? $_SERVER['SCRIPT_NAME'] : '';
    $base = rtrim(str_replace('\\', '/', dirname($scriptName)), '/');
    if ($base !== '' && strpos($requestPath, $base) === 0) {
        $requestPath = substr($requestPath, strlen($base));
    }
    $requestPath = trim($requestPath, '/');
    if ($requestPath === '' || $requestPath === 'index.php') {
        $page = 'home';
    } else {
        $segments = explode('/', $requestPath);
        $page = $segments[0];
        if (count($segments) > 1 && !isset($_GET['id'])) {
            $_GET['id'] = urldecode($segments[1]);
        }
    }
}

$titles = array(
    'home' => 'Overview',
    'subject' => 'Subject',
    'predicate' => 'Predicate',
    'document' => 'Document',
    'vocab' => 'Vocabulary',
    'issues' => 'Issues',
    'part' => 'Part',
);

$title = isset($titles[$page]) ? $titles[$page] : 'Not found';
render_header($title);
try {
    switch ($page) {
        case 'home':
            page_home();
            break;
        case 'subject':
            page_subject();
            break;
        case 'predicate':
            page_predicate();
            break;
        case 'document':
            page_document();
            break;
        case 'vocab':
            page_vocab();
            break;
        case 'issues':
            page_issues();
            break;
        case 'part':
            page_part();
            break;
        default:
            http_response_code(404);
            render_error('Unknown page: ' . $page);
            break;
    }
} catch (Exception $e) {
    http_response_code(500);
    render_error($e->getMessage());
}
render_footer();

function page_home()
{
    $counts = array(
        'Documents' => scalar('SELECT COUNT(*) FROM document'),
        'Batches' => scalar('SELECT COUNT(*) FROM batch'),
        'Loadings' => scalar('SELECT COUNT(*) FROM loading'),
        'Statements' => scalar('SELECT COUNT(*) FROM statement'),
        'Parse issues' => scalar('SELECT COUNT(*) FROM parse_issue'),
        'Conflicts' => scalar('SELECT COUNT(*) FROM conflict'),
        'Vocabulary terms' => scalar('SELECT COUNT(*) FROM vocab_term'),
    );

    echo '<table><tr><th>Item</th><th>Count</th></tr>';
    foreach ($counts as $label => $count) {
        echo '<tr><td>' . h($label) . '</td><td>' . h($count) . '</td></tr>';
    }
    echo '</table>';

    echo '<h2>Recent batches</h2>';
    $batches = fetch_all('SELECT * FROM batch ORDER BY started_at DESC, iri LIMIT 10');
    if (count($batches) === 0) {
        echo '<p class="muted">No batches have been loaded.</p>';
    } else {
        echo '<table><tr><th>Batch</th><th>Started</th><th>Importer</th>'
            . '<th>Options</th></tr>';
        foreach ($batches as $batch) {
            echo '<tr><td><code>' . h($batch['iri']) . '</code></td>'
                . '<td>' . h($batch['started_at']) . '</td>'
                . '<td>' . h($batch['importer_version']) . '</td>'
                . '<td><code>' . h($batch['options_json']) . '</code></td></tr>';
        }
        echo '</table>';
    }

    echo '<h2>Sources</h2>';
    $documents = fetch_all(
        'SELECT * FROM document ORDER BY iri LIMIT 50'
    );
    if (count($documents) === 0) {
        echo '<p class="muted">No documents have been loaded.</p>';
    } else {
        echo '<table><tr><th>Document</th><th>Source URL</th><th>Format</th>'
            . '<th>Bytes</th></tr>';
        foreach ($documents as $document) {
            echo '<tr><td>' . resource_link($document['iri']) . '</td>'
                . '<td>' . h($document['source_url']) . '</td>'
                . '<td>' . h($document['detected_format']) . '</td>'
                . '<td>' . h($document['byte_length']) . '</td></tr>';
        }
        echo '</table>';
    }

    echo '<h2>Issue summary</h2>';
    $issues = fetch_all(
        'SELECT severity, COUNT(*) AS n FROM parse_issue'
        . ' GROUP BY severity ORDER BY severity'
    );
    if (count($issues) === 0) {
        echo '<p class="muted">No parse issues.</p>';
    } else {
        echo '<table><tr><th>Severity</th><th>Count</th></tr>';
        foreach ($issues as $issue) {
            echo '<tr><td>' . h($issue['severity']) . '</td>'
                . '<td>' . h($issue['n']) . '</td></tr>';
        }
        echo '</table>';
    }
}

function page_subject()
{
    $iri = require_iri('iri');
    echo '<p><code>' . h($iri) . '</code></p>';
    echo '<p><a href="' . h(document_url($iri)) . '">Document metadata</a></p>';

    $rows = fetch_all(
        'SELECT * FROM statement WHERE subject = ? ORDER BY predicate, id LIMIT 1000',
        array($iri)
    );
    if (count($rows) === 0) {
        echo '<p class="muted">No statements with this subject.</p>';
        return;
    }

    $groups = array();
    foreach ($rows as $row) {
        $groups[$row['predicate']][] = $row;
    }
    foreach ($groups as $predicate => $statements) {
        echo '<h2><a href="' . h(predicate_url($predicate)) . '">'
            . h(short_iri($predicate)) . '</a></h2>';
        echo '<table><tr><th>Object</th><th>Source</th><th>Location</th>'
            . '<th>Batch</th></tr>';
        foreach ($statements as $row) {
            echo '<tr><td>'
                . render_value($row['object'], $row['object_kind'], $row['datatype'])
                . '</td><td>' . resource_link($row['source']) . '</td>'
                . '<td><code>' . h(location_text($row)) . '</code></td>'
                . '<td><code>' . h($row['batch_iri']) . '</code></td></tr>';
        }
        echo '</table>';
    }
}

function page_predicate()
{
    $iri = require_iri('iri');
    echo '<p><code>' . h($iri) . '</code></p>';

    $rows = fetch_all(
        'SELECT subject, COUNT(*) AS n FROM statement WHERE predicate = ?'
        . ' GROUP BY subject ORDER BY subject LIMIT 1000',
        array($iri)
    );
    if (count($rows) === 0) {
        echo '<p class="muted">No subjects use this predicate.</p>';
        return;
    }
    echo '<table><tr><th>Subject</th><th>Statements</th></tr>';
    foreach ($rows as $row) {
        echo '<tr><td>' . resource_link($row['subject']) . '</td>'
            . '<td>' . h($row['n']) . '</td></tr>';
    }
    echo '</table>';
}

function page_document()
{
    $iri = require_iri('iri');
    $document = fetch_one('SELECT * FROM document WHERE iri = ?', array($iri));
    if ($document === null) {
        render_error('Document not found: ' . $iri);
        return;
    }

    echo '<table>';
    foreach ($document as $key => $value) {
        echo '<tr><th>' . h($key) . '</th><td><code>' . h($value) . '</code></td></tr>';
    }
    echo '</table>';

    echo '<p><a href="' . h(subject_url($iri)) . '">All statements about this document</a></p>';

    echo '<h2>Loadings</h2>';
    $loadings = fetch_all(
        'SELECT * FROM loading WHERE document_iri = ? ORDER BY iri',
        array($iri)
    );
    if (count($loadings) === 0) {
        echo '<p class="muted">No loadings.</p>';
    } else {
        echo '<table><tr><th>Loading</th><th>Batch</th></tr>';
        foreach ($loadings as $loading) {
            echo '<tr><td><code>' . h($loading['iri']) . '</code></td>'
                . '<td><code>' . h($loading['batch_iri']) . '</code></td></tr>';
        }
        echo '</table>';
    }

    echo '<h2>Parse issues</h2>';
    $issues = fetch_all(
        'SELECT * FROM parse_issue WHERE document_iri = ?'
        . ' ORDER BY line, column, id',
        array($iri)
    );
    if (count($issues) === 0) {
        echo '<p class="muted">No parse issues.</p>';
    } else {
        echo '<table><tr><th>Location</th><th>Severity</th><th>Code</th>'
            . '<th>Message</th></tr>';
        foreach ($issues as $issue) {
            echo '<tr><td><code>' . h(location_text($issue)) . '</code></td>'
                . '<td>' . h($issue['severity']) . '</td>'
                . '<td>' . h($issue['code']) . '</td>'
                . '<td>' . h($issue['message']) . '</td></tr>';
        }
        echo '</table>';
    }
}

function page_vocab()
{
    $terms = fetch_all('SELECT * FROM vocab_term ORDER BY iri');
    $used = array();
    foreach (fetch_all('SELECT DISTINCT predicate AS iri FROM statement') as $row) {
        $used[$row['iri']] = true;
    }
    foreach (fetch_all(
        "SELECT DISTINCT object AS iri FROM statement"
        . " WHERE predicate = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type'"
        . " AND object_kind = 'iri'"
    ) as $row) {
        $used[$row['iri']] = true;
    }

    $rows = array();
    foreach ($terms as $term) {
        $status = isset($used[$term['iri']]) ? 'ok' : 'unused';
        if (strpos((string) $term['defined_in'], ',') !== false) {
            $status = 'duplicate';
        }
        $rows[] = array(
            'term' => $term['iri'],
            'status' => $status,
            'kind' => $term['kind'],
            'used' => isset($used[$term['iri']]) ? 'yes' : 'no',
            'defined_in' => $term['defined_in'],
            'description' => $term['comment'] !== null
                ? $term['comment'] : $term['label'],
        );
        unset($used[$term['iri']]);
    }
    foreach (array_keys($used) as $iri) {
        $status = strpos($iri, 'tag:nuke24.net,2026-11:togetl:') === 0
            ? 'proposed' : 'undefined';
        $rows[] = array(
            'term' => $iri,
            'status' => $status,
            'kind' => '',
            'used' => 'yes',
            'defined_in' => '',
            'description' => '',
        );
    }
    usort($rows, function ($a, $b) {
        return strcmp($a['term'], $b['term']);
    });

    $counts = array();
    foreach ($rows as $row) {
        $counts[$row['status']] = isset($counts[$row['status']])
            ? $counts[$row['status']] + 1 : 1;
    }
    ksort($counts);

    echo '<p>';
    foreach ($counts as $status => $count) {
        echo '<span class="muted">' . h($status) . ': ' . h($count) . '</span> ';
    }
    echo '</p>';

    echo '<table><tr><th>Term</th><th>Status</th><th>Kind</th><th>Used</th>'
        . '<th>Defined in</th><th>Description</th></tr>';
    foreach ($rows as $row) {
        echo '<tr><td><a href="' . h(subject_url($row['term'])) . '">'
            . h(short_iri($row['term'])) . '</a></td>'
            . '<td>' . h($row['status']) . '</td>'
            . '<td>' . h($row['kind']) . '</td>'
            . '<td>' . h($row['used']) . '</td>'
            . '<td>' . h($row['defined_in']) . '</td>'
            . '<td>' . h($row['description']) . '</td></tr>';
    }
    echo '</table>';
}

function page_issues()
{
    echo '<h2>Parse issues</h2>';
    $issues = fetch_all(
        'SELECT * FROM parse_issue ORDER BY document_iri, line, column, id LIMIT 500'
    );
    if (count($issues) === 0) {
        echo '<p class="muted">No parse issues.</p>';
    } else {
        echo '<table><tr><th>Document</th><th>Location</th><th>Severity</th>'
            . '<th>Code</th><th>Message</th><th>Raw</th></tr>';
        foreach ($issues as $issue) {
            echo '<tr><td>' . resource_link($issue['document_iri']) . '</td>'
                . '<td><code>' . h(location_text($issue)) . '</code></td>'
                . '<td>' . h($issue['severity']) . '</td>'
                . '<td>' . h($issue['code']) . '</td>'
                . '<td>' . h($issue['message']) . '</td>'
                . '<td><code>' . h($issue['raw']) . '</code></td></tr>';
        }
        echo '</table>';
    }

    echo '<h2>Conflicts</h2>';
    $conflicts = fetch_all(
        'SELECT * FROM conflict ORDER BY document_iri, id LIMIT 500'
    );
    if (count($conflicts) === 0) {
        echo '<p class="muted">No conflicts.</p>';
    } else {
        echo '<table><tr><th>Document</th><th>Entry type</th><th>Field</th>'
            . '<th>Count</th><th>First location</th></tr>';
        foreach ($conflicts as $conflict) {
            echo '<tr><td>'
                . ($conflict['document_iri'] !== null
                    ? resource_link($conflict['document_iri'])
                    : '<span class="muted">-</span>')
                . '</td>'
                . '<td>' . h($conflict['entry_type']) . '</td>'
                . '<td>' . h($conflict['field']) . '</td>'
                . '<td>' . h($conflict['occurrence_count']) . '</td>'
                . '<td><code>' . h($conflict['first_location']) . '</code></td></tr>';
        }
        echo '</table>';
    }
}

function page_part()
{
    $iri = isset($_GET['iri']) ? trim((string) $_GET['iri']) : '';
    if ($iri === '') {
        $id = isset($_GET['id']) ? trim((string) $_GET['id']) : '';
        if ($id === '') {
            throw new InvalidArgumentException('Missing part id');
        }
        $iri = 'tag:nuke24.net,2026-11:3dparts:part:' . $id;
    }

    $part = fetch_one('SELECT * FROM part WHERE iri = ?', array($iri));
    if ($part === null) {
        render_error('Part not found: ' . $iri);
        return;
    }

    echo '<p><code>' . h($part['iri']) . '</code></p>';
    echo '<table><tr><th>Name</th><td>' . h($part['name']) . '</td></tr>'
        . '<tr><th>Description</th><td>' . h($part['short_description']) . '</td></tr>'
        . '<tr><th>Created</th><td>' . h($part['created']) . '</td></tr></table>';

    $files = fetch_all(
        'SELECT * FROM part_file WHERE part_iri = ? ORDER BY relation, filename',
        array($iri)
    );
    echo '<h2>Files</h2>';
    if (count($files) === 0) {
        echo '<p class="muted">No file references.</p>';
    } else {
        echo '<table><tr><th>Relation</th><th>File</th><th>Name</th></tr>';
        foreach ($files as $file) {
            echo '<tr><td>' . h(short_iri($file['relation'])) . '</td>'
                . '<td>' . render_value($file['file_iri'], 'iri') . '</td>'
                . '<td><code>' . h($file['filename']) . '</code></td></tr>';
        }
        echo '</table>';
    }

    $aliases = fetch_all(
        'SELECT * FROM part_alias WHERE part_iri = ? ORDER BY alias',
        array($iri)
    );
    if (count($aliases) > 0) {
        echo '<h2>Aliases</h2><ul>';
        foreach ($aliases as $alias) {
            echo '<li><code>' . h($alias['alias']) . '</code></li>';
        }
        echo '</ul>';
    }

    render_part_links(
        'Tags',
        'SELECT object AS iri FROM statement WHERE subject = ?'
        . " AND predicate = 'tag:nuke24.net,2026-11:general:tag'"
        . " AND object_kind = 'literal' ORDER BY object",
        $iri,
        false
    );
    render_part_links(
        'Member of',
        'SELECT object AS iri FROM statement WHERE subject = ?'
        . " AND predicate = 'tag:nuke24.net,2026-11:general:isMemberOf'"
        . " AND object_kind = 'iri' ORDER BY object",
        $iri,
        true
    );
    render_part_links(
        'Members',
        'SELECT subject AS iri FROM statement WHERE object = ?'
        . " AND predicate = 'tag:nuke24.net,2026-11:general:member'"
        . " AND subject LIKE 'tag:nuke24.net,2026-11:3dparts:part:%' ORDER BY subject",
        $iri,
        true
    );
    render_part_links(
        'See also',
        'SELECT object AS iri FROM statement WHERE subject = ?'
        . " AND predicate = 'tag:nuke24.net,2026-11:general:seeAlso'"
        . " AND object_kind = 'iri' ORDER BY object",
        $iri,
        true
    );
    render_part_links(
        'Obsoleted by',
        'SELECT object AS iri FROM statement WHERE subject = ?'
        . " AND predicate = 'tag:nuke24.net,2026-11:general:obsoletedBy'"
        . " AND object_kind = 'iri' ORDER BY object",
        $iri,
        true
    );

    echo '<p><a href="' . h(subject_url($iri)) . '">All statements</a></p>';
}

function render_part_links($heading, $sql, $iri, $asLink)
{
    $rows = fetch_all($sql, array($iri));
    if (count($rows) === 0) {
        return;
    }
    echo '<h2>' . h($heading) . '</h2><ul>';
    foreach ($rows as $row) {
        if ($asLink) {
            echo '<li>' . resource_link($row['iri']) . '</li>';
        } else {
            echo '<li><code>' . h($row['iri']) . '</code></li>';
        }
    }
    echo '</ul>';
}
