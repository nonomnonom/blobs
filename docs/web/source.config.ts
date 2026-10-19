import { defineConfig } from 'fumadocs-mdx/config';
import { remarkMdxMermaid } from 'fumadocs-core/mdx-plugins';

export default defineConfig({
    mdxOptions: {
        remarkPlugins: [remarkMdxMermaid],
        rehypeCodeOptions: {
            themes: {
                light: 'github-light-high-contrast',
                dark: 'github-dark-high-contrast',
            },
        },
    },
});
