/**
 * Copies the release version from package.json into the Android app, keeping
 * `versionCode` strictly increasing for the existing signing identity.
 */

import { readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(import.meta.dirname, '..');
const gradlePath = path.join('android', 'app', 'build.gradle.kts');

function parseSemver(value) {
    const match = /^(\d+)\.(\d+)\.(\d+)$/.exec(value);
    if (!match) {
        throw new Error(
            `Expected a plain MAJOR.MINOR.PATCH version, got "${value}".`,
        );
    }
    return match.slice(1).map(Number);
}

function compareSemver(left, right) {
    const a = parseSemver(left);
    const b = parseSemver(right);
    for (let index = 0; index < a.length; index += 1) {
        if (a[index] !== b[index]) return a[index] - b[index];
    }
    return 0;
}

function readSingle(source, pattern, label) {
    const matches = [...source.matchAll(new RegExp(pattern.source, 'g'))];
    if (matches.length !== 1) {
        throw new Error(
            `Expected exactly one ${label} entry in build.gradle.kts, found ${matches.length}.`,
        );
    }
    return matches[0];
}

export function syncAndroidVersion(source, version) {
    parseSemver(version);
    const nameMatch = readSingle(source, /versionName = "([^"]+)"/, 'versionName');
    const codeMatch = readSingle(source, /versionCode = (\d+)/, 'versionCode');
    const previousVersionName = nameMatch[1];
    const previousVersionCode = Number(codeMatch[1]);
    const ordering = compareSemver(version, previousVersionName);
    if (ordering < 0) {
        throw new Error(
            `Refusing to downgrade the app version: build.gradle.kts is ${previousVersionName}, package.json is ${version}.`,
        );
    }
    if (ordering === 0) {
        return {
            source,
            versionName: version,
            versionCode: previousVersionCode,
            changed: false,
        };
    }
    const versionCode = previousVersionCode + 1;
    const updated = source
        .replace(/versionName = "[^"]+"/, `versionName = "${version}"`)
        .replace(/versionCode = \d+/, `versionCode = ${versionCode}`);
    return {
        source: updated,
        versionName: version,
        versionCode,
        previousVersionName,
        changed: true,
    };
}

async function main() {
    const checkOnly = process.argv.slice(2).includes('--check');
    const packageJson = JSON.parse(
        await readFile(path.join(repoRoot, 'package.json'), 'utf8'),
    );
    const target = path.join(repoRoot, gradlePath);
    const result = syncAndroidVersion(
        await readFile(target, 'utf8'),
        packageJson.version,
    );
    if (result.changed && checkOnly) {
        throw new Error(
            `${gradlePath} is ${result.previousVersionName} but package.json is ` +
                `${packageJson.version}. Run \`npm run release:version\`.`,
        );
    }
    if (result.changed) {
        await writeFile(target, result.source, 'utf8');
        console.log(
            `Wrote ${gradlePath}: versionName "${result.versionName}", ` +
                `versionCode ${result.versionCode}.`,
        );
    } else {
        console.log(
            `${gradlePath} already matches package.json: versionName ` +
                `"${result.versionName}", versionCode ${result.versionCode}.`,
        );
    }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    main().catch((error) => {
        console.error(String(error.message ?? error));
        process.exitCode = 1;
    });
}
