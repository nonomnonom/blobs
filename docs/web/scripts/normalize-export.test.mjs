import {
    mkdtemp,
    mkdir,
    writeFile,
    readFile,
    rm,
    readdir,
} from 'node:fs/promises';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import assert from 'node:assert/strict';
import test from 'node:test';
import { normalizeExport } from './normalize-export.mjs';

test('normalizes Windows segment paths and leaves page files intact', async (t) => {
    const root = await mkdtemp(join(tmpdir(), 'blobs-docs-export-'));
    t.after(() => rm(root, { recursive: true, force: true }));
    const nested = join(root, 'docs', 'chat', '__next.docs', '$oc$slug');
    await mkdir(nested, { recursive: true });
    await writeFile(join(nested, '__PAGE__.txt'), 'flight data');
    await writeFile(join(root, 'docs', 'chat', 'index.html'), 'page');
    assert.equal(await normalizeExport(root), 1);
    assert.equal(
        await readFile(
            join(root, 'docs', 'chat', '__next.docs.$oc$slug.__PAGE__.txt'),
            'utf8',
        ),
        'flight data',
    );
    assert.equal(
        await readFile(join(root, 'docs', 'chat', 'index.html'), 'utf8'),
        'page',
    );
    assert.equal(await normalizeExport(root), 0);
    assert.deepEqual((await readdir(join(root, 'docs', 'chat'))).sort(), [
        '__next.docs.$oc$slug.__PAGE__.txt',
        'index.html',
    ]);
});

test('never overwrites an existing dotted export', async (t) => {
    const root = await mkdtemp(join(tmpdir(), 'blobs-docs-export-'));
    t.after(() => rm(root, { recursive: true, force: true }));
    await mkdir(join(root, '__next.docs'));
    await writeFile(join(root, '__next.docs', '__PAGE__.txt'), 'nested');
    await writeFile(join(root, '__next.docs.__PAGE__.txt'), 'existing');
    await assert.rejects(normalizeExport(root), /Refusing to replace/);
    assert.equal(
        await readFile(join(root, '__next.docs.__PAGE__.txt'), 'utf8'),
        'existing',
    );
});
