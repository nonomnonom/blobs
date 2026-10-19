import { renderMermaidSVG } from 'beautiful-mermaid';

export function Mermaid({
    chart,
    title = 'How Blobs works',
}: {
    chart: string;
    title?: string;
}) {
    const svg = renderMermaidSVG(chart, {
        bg: 'var(--color-fd-muted)',
        fg: 'var(--color-fd-foreground)',
        accent: 'var(--color-fd-primary)',
        line: 'var(--color-fd-muted-foreground)',
        muted: 'var(--color-fd-muted-foreground)',
        surface: 'var(--color-fd-card)',
        border: 'var(--color-fd-border)',
        font: 'DM Sans',
        transparent: true,
        padding: 24,
        // The renderer adds Google Fonts imports; Blobs serves its own local fonts.
    }).replace(/@import\s+url\([^)]*\);/g, '');

    return (
        <figure className="mermaid-figure">
            <div
                className="mermaid-viewport"
                tabIndex={0}
                role="img"
                aria-label={title}
                dangerouslySetInnerHTML={{ __html: svg }}
            />
            <figcaption>{title}</figcaption>
        </figure>
    );
}
