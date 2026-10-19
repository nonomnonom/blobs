import defaultMdxComponents from 'fumadocs-ui/mdx';
import type { MDXComponents } from 'mdx/types';
import type { ComponentProps, HTMLAttributes } from 'react';
import { CodeBlock, Pre } from 'fumadocs-ui/components/codeblock';
import { LibraryTable, ModelTable } from '@/components/asset-tables';
import { Mermaid } from '@/components/mermaid';
import { ScreenGallery } from '@/components/screen-gallery';

function SourceLink({ href, ...props }: ComponentProps<'a'>) {
    const Link = defaultMdxComponents.a;
    const resolved = href?.startsWith('../')
        ? `https://github.com/nonomnonom/blobs/blob/main/${href.slice(3)}`
        : href;
    return <Link href={resolved} {...props} />;
}

export function getMDXComponents(components?: MDXComponents) {
    return {
        ...defaultMdxComponents,
        a: SourceLink,
        pre: CodeExample,
        LibraryTable,
        ModelTable,
        Mermaid,
        ScreenGallery,
        ...components,
    } satisfies MDXComponents;
}

function CodeExample(props: HTMLAttributes<HTMLPreElement>) {
    return (
        <CodeBlock
            {...props}
            viewportProps={{ 'aria-label': props.title ?? 'Code example' }}
        >
            <Pre>{props.children}</Pre>
        </CodeBlock>
    );
}

export const useMDXComponents = getMDXComponents;

declare global {
    type MDXProvidedComponents = ReturnType<typeof getMDXComponents>;
}
