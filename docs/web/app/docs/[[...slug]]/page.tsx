import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import {
    DocsBody,
    DocsDescription,
    DocsPage,
    DocsTitle,
} from 'fumadocs-ui/page';
import { getMDXComponents } from '@/components/mdx';
import { DocsTOC } from '@/components/docs-toc';
import { TOC, TOCProvider } from 'fumadocs-ui/layouts/docs/page/slots/toc';
import { source } from '@/lib/source';

type Props = { params: Promise<{ slug?: string[] }> };

export default async function Page({ params }: Props) {
    const { slug } = await params;
    const page = source.getPage(slug);
    if (!page) notFound();
    const Content = page.data.body;
    const hasIncludedTitle = page.slugs.join('/') === 'architecture';

    return (
        <DocsPage
            toc={page.data.toc}
            tableOfContent={{
                container: { role: 'navigation', 'aria-label': 'On this page' },
            }}
            slots={{
                toc: { provider: TOCProvider, main: TOC, popover: DocsTOC },
            }}
        >
            {!hasIncludedTitle && <DocsTitle>{page.data.title}</DocsTitle>}
            {!hasIncludedTitle && (
                <DocsDescription>{page.data.description}</DocsDescription>
            )}
            <DocsBody>
                <Content components={getMDXComponents()} />
            </DocsBody>
        </DocsPage>
    );
}

export function generateStaticParams() {
    return source.generateParams();
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
    const { slug } = await params;
    const page = source.getPage(slug);
    if (!page) notFound();
    return { title: page.data.title, description: page.data.description };
}
