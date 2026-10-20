import catalog from '../../../android/app/src/main/assets/package-catalog.json';
import mdxComponents from 'fumadocs-ui/mdx';

const Table = mdxComponents.table;

function downloadSize(bytes: number) {
    if (bytes >= 1_000_000_000)
        return `${(bytes / 1_000_000_000).toFixed(2)} GB`;
    if (bytes >= 1_000_000) return `${(bytes / 1_000_000).toFixed(1)} MB`;
    return `${(bytes / 1_000).toFixed(1)} KB`;
}

export function ModelTable() {
    return (
        <Table className="min-w-[420px]">
            <caption>
                Models in the app’s bundled download list. Download sizes use
                decimal units; RAM is shown in GiB.
            </caption>
            <thead>
                <tr>
                    <th scope="col">Model</th>
                    <th scope="col">Download</th>
                    <th scope="col">Minimum RAM</th>
                </tr>
            </thead>
            <tbody>
                {catalog.packages
                    .filter((asset) => asset.role === 'model')
                    .map((asset) => (
                        <tr key={asset.manifest.id}>
                            <th scope="row">{asset.manifest.title}</th>
                            <td>{downloadSize(asset.download_bytes)}</td>
                            <td>
                                {asset.compatibility.minimum_ram_mb! / 1024} GiB
                            </td>
                        </tr>
                    ))}
            </tbody>
        </Table>
    );
}

export function LibraryTable() {
    return (
        <Table className="min-w-[680px]">
            <caption>
                Libraries in the app’s bundled download list. All are in
                English. Download sizes are compressed; installation also needs
                temporary space.
            </caption>
            <thead>
                <tr>
                    <th scope="col">Library</th>
                    <th scope="col">Download</th>
                    <th scope="col">What’s inside</th>
                </tr>
            </thead>
            <tbody>
                {catalog.packages
                    .filter((asset) => asset.role !== 'model')
                    .map((asset) => (
                        <tr key={asset.manifest.id}>
                            <th scope="row">
                                {asset.manifest.title}
                                {asset.role === 'starter' ? ' (starter)' : ''}
                            </th>
                            <td className="whitespace-nowrap">
                                {downloadSize(asset.download_bytes)}
                            </td>
                            <td>{asset.description}</td>
                        </tr>
                    ))}
            </tbody>
        </Table>
    );
}
