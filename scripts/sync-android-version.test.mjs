import assert from 'node:assert/strict';
import test from 'node:test';

import { syncAndroidVersion } from './sync-android-version.mjs';

const gradleFixture = `
    defaultConfig {
        applicationId = "dev.blobs.app"
        minSdk = 28
        versionCode = 18
        versionName = "0.3.14"
    }
`;

test('adopts a newer version and advances versionCode', () => {
    const result = syncAndroidVersion(gradleFixture, '0.3.15');
    assert.equal(result.changed, true);
    assert.equal(result.versionCode, 19);
    assert.match(result.source, /versionName = "0\.3\.15"/);
    assert.match(result.source, /versionCode = 19/);
    assert.equal(
        result.source
            .replace(/0\.3\.15/g, '0.3.14')
            .replace(/versionCode = 19/, 'versionCode = 18'),
        gradleFixture,
    );
});

test('advances a major bump by one versionCode', () => {
    const result = syncAndroidVersion(gradleFixture, '1.0.0');
    assert.equal(result.versionName, '1.0.0');
    assert.equal(result.versionCode, 19);
});

test('is idempotent once the versions match', () => {
    const result = syncAndroidVersion(gradleFixture, '0.3.14');
    assert.equal(result.changed, false);
    assert.equal(result.source, gradleFixture);
    assert.equal(result.versionCode, 18);
});

test('refuses to downgrade an already published version', () => {
    assert.throws(
        () => syncAndroidVersion(gradleFixture, '0.3.13'),
        /downgrade/,
    );
});

test('refuses versions Android cannot express', () => {
    assert.throws(
        () => syncAndroidVersion(gradleFixture, '1.0.0-rc.1'),
        /MAJOR\.MINOR\.PATCH/,
    );
});

test('refuses a gradle file with duplicate version entries', () => {
    const duplicated = `${gradleFixture}\n        versionName = "0.3.14"`;
    assert.throws(
        () => syncAndroidVersion(duplicated, '0.4.0'),
        /exactly one versionName/,
    );
});
