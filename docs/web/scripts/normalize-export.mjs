import { readdir, rename, rmdir, lstat } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

// Next.js 16 on Windows emits nested RSC paths that its client requests dotted.
// Remove when https://github.com/vercel/next.js/issues/85374 is fixed upstream.
export async function normalizeExport(root) {
    let normalized = 0;

    async function flatten(directory, parent, prefix) {
        for (const entry of await readdir(directory, { withFileTypes: true })) {
            const source = join(directory, entry.name);
            if (entry.isDirectory()) {
                await flatten(source, parent, `${prefix}.${entry.name}`);
                await rmdir(source);
            } else if (entry.isFile() && entry.name.endsWith('.txt')) {
                const target = join(parent, `${prefix}.${entry.name}`);
                try {
                    await lstat(target);
                    throw new Error(
                        `Refusing to replace an existing export: ${target}`,
                    );
                } catch (error) {
                    if (error.code !== 'ENOENT') throw error;
                }
                await rename(source, target);
                normalized++;
            } else {
                throw new Error(
                    `Unexpected entry in an RSC export directory: ${source}`,
                );
            }
        }
    }

    async function visit(directory) {
        for (const entry of await readdir(directory, { withFileTypes: true })) {
            if (!entry.isDirectory()) continue;
            const child = join(directory, entry.name);
            if (entry.name.startsWith('__next.')) {
                await flatten(child, directory, entry.name);
                await rmdir(child);
            } else if (entry.name !== '_next') {
                await visit(child);
            }
        }
    }

    await visit(resolve(root));
    return normalized;
}

if (
    process.argv[1] &&
    import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
    const count = await normalizeExport(
        fileURLToPath(new URL('../out/', import.meta.url)),
    );
    console.log(`Normalized ${count} Windows RSC export paths.`);
}
