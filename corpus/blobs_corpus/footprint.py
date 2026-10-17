"""Verify an offline kit and calculate its storage footprint without copying artifacts."""
import argparse
import hashlib
import json
from pathlib import Path
import re

from .extract import write_json
from .package import verify_package


BOUNTY_LIMIT_BYTES = 50_000_000_000


def sha(path: Path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def read_installer_policy(path: Path):
    source = path.read_bytes()
    text = source.decode('utf-8')
    return installer_policy_text(text, hashlib.sha256(source).hexdigest())


def installer_policy_text(text: str, source_sha256: str):
    headroom = re.findall(r'expected\.bytes - resumeBytes \+ (\d+)L \* 1024 \* 1024', text)
    caps = re.findall(r'expected\.bytes < ([\d_]+)L', text)
    if headroom != ['64'] or caps != ['48_000_000_000']:
        raise ValueError('Android import storage policy changed; review this calculator')
    return dict(import_headroom_bytes=int(headroom[0]) * 1024 * 1024,
                installed_payload_cap_bytes=int(caps[0].replace('_', '')),
                source_sha256=source_sha256)


def read_apk_report(path: Path):
    report = json.loads(path.read_text(encoding='utf-8'))
    required = {'apk', 'application_id', 'version_code', 'version_name', 'bytes', 'sha256',
                'internet_permission', 'zip_alignment_16kb'}
    if not isinstance(report, dict) or not required <= report.keys():
        raise ValueError('Incomplete APK verification report')
    if (type(report['bytes']) is not int or report['bytes'] <= 0 or
            not re.fullmatch(r'[a-f0-9]{64}', report['sha256']) or
            type(report['version_code']) is not int or report['version_code'] <= 0 or
            not isinstance(report['version_name'], str) or not report['version_name'] or
            report['internet_permission'] is not False or report['zip_alignment_16kb'] is not True):
        raise ValueError('APK report does not describe an offline aligned build')
    return report


def storage_values(packages, apk_bytes, import_headroom):
    incoming = sum(item['package']['bytes'] for item in packages)
    installed = sum(item['manifest']['bytes'] for item in packages)
    cumulative = 0
    retained_steps = []
    move_steps = []
    for item in packages:
        payload = item['manifest']['bytes']
        package_bytes = item['package']['bytes']
        retained_steps.append(dict(package=item['package']['file'],
            bytes=apk_bytes + incoming + cumulative + payload,
            composition='APK archive + all incoming packages + earlier installed payloads + current staging payload'))
        move_steps.append(dict(package=item['package']['file'],
            bytes=apk_bytes + cumulative + package_bytes + payload,
            composition='APK archive + earlier installed payloads + current incoming package + current staging payload'))
        cumulative += payload
    return dict(
        distribution_bytes=apk_bytes + incoming,
        incoming_package_bytes=incoming,
        installed_payload_bytes=installed,
        known_steady_bytes=apk_bytes + installed,
        retained_sources=dict(peak_bytes=max(step['bytes'] for step in retained_steps), steps=retained_steps),
        move_each_source_after_import=dict(peak_bytes=max(step['bytes'] for step in move_steps), steps=move_steps),
        importer_allocatable_thresholds=[dict(package=item['package']['file'],
            fresh_import_threshold_bytes=item['manifest']['bytes'] + import_headroom,
            comparison='device allocatable bytes > threshold')
            for item in packages])


def calculate(apk_report_path: Path, package_paths, installer_source: Path):
    apk = read_apk_report(apk_report_path)
    if len(package_paths) != 2 or len(set(path.resolve() for path in package_paths)) != 2:
        raise ValueError('A release footprint requires one distinct model and one library package')
    packages = []
    for path in package_paths:
        manifest = verify_package(path)
        packages.append(dict(manifest=manifest,
            package=dict(file=path.name, bytes=path.stat().st_size, sha256=sha(path))))
    if [item['manifest']['kind'] for item in packages].count('model') != 1 or [
        item['manifest']['kind'] for item in packages
    ].count('library') != 1:
        raise ValueError('A release footprint requires exactly one model and one library')
    policy = read_installer_policy(installer_source)
    storage = storage_values(packages, apk['bytes'], policy['import_headroom_bytes'])
    installed = storage['installed_payload_bytes']
    strict_cap_passes = installed < policy['installed_payload_cap_bytes']
    bounty_passes = storage['known_steady_bytes'] <= BOUNTY_LIMIT_BYTES
    return dict(format=1, complete=True,
        scope=('Verified package bytes and deterministic storage accounting. APK bytes come from the '
               'specified verification report; Android installed APK expansion, conversations, exports, '
               'filesystem overhead and update coexistence are not measured. This is not device RAM or performance evidence.'),
        apk_report=dict(file=apk_report_path.as_posix(), sha256=sha(apk_report_path), value=apk),
        installer_policy=dict(file=installer_source.as_posix(), **policy), packages=packages,
        import_order=[item['package']['file'] for item in packages], storage=storage,
        checks=dict(
            android_installed_payload_cap=dict(limit_bytes=policy['installed_payload_cap_bytes'],
                selected_bytes=installed, margin_bytes=policy['installed_payload_cap_bytes'] - installed,
                passes=strict_cap_passes, comparison='selected installed payload bytes < limit'),
            bounty_storage_limit=dict(limit_bytes=BOUNTY_LIMIT_BYTES,
                known_steady_bytes=storage['known_steady_bytes'],
                margin_before_unmeasured_app_data=BOUNTY_LIMIT_BYTES - storage['known_steady_bytes'],
                passes=bounty_passes,
                comparison='APK archive bytes + selected installed payload bytes <= limit')),
        interpretation=dict(
            steady='Known lower bound after imported source packages are removed from phone storage.',
            retained_sources='Peak assumes both incoming .blobs files remain on the phone for the full import sequence.',
            move_each_source='Peak assumes each .blobs file is moved off phone storage immediately after successful import.',
            update='Not included. Updating while an old edition remains installed needs old payload + incoming package + new staging payload.',
            importer=('Before each fresh import, the app requires device allocatable bytes to be '
                      'strictly greater than the listed threshold.')))


def release_report(apk_report_path: Path, package_paths, installer_source: Path):
    """Create a path-independent footprint manifest suitable for a release kit."""
    result = calculate(apk_report_path, package_paths, installer_source)
    result['apk_report']['file'] = 'APK-VERIFICATION.json'
    result['installer_policy']['file'] = (
        'android/app/src/main/kotlin/dev/blobs/app/data/packages/PackageInstaller.kt'
    )
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk-report', type=Path, required=True)
    parser.add_argument('--package', type=Path, action='append', required=True,
                        help='Repeat twice in intended import order: one model and one library')
    parser.add_argument('--installer-source', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise FileExistsError(args.output)
    report = calculate(args.apk_report, args.package, args.installer_source)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    write_json(args.output, report)
    summary = report['storage']
    print(json.dumps({key: summary[key] for key in [
        'distribution_bytes', 'installed_payload_bytes', 'known_steady_bytes',
        'retained_sources', 'move_each_source_after_import']}, indent=2))


if __name__ == '__main__':
    main()
