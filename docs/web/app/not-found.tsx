import Link from 'next/link';

export default function NotFound() {
    return (
        <main className="not-found">
            <p className="eyebrow">A little detour</p>
            <h1>This page wandered off.</h1>
            <p>Try searching the guides, or head back to a familiar place.</p>
            <Link href="/docs" className="primary-link">
                Back to the guides <span aria-hidden="true">→</span>
            </Link>
        </main>
    );
}
