import { createMDX } from 'fumadocs-mdx/next';
import { fileURLToPath } from 'node:url';

const withMDX = createMDX();

/** @type {import('next').NextConfig} */
export default withMDX({
    output: 'export',
    trailingSlash: true,
    reactStrictMode: true,
    images: { unoptimized: true },
    turbopack: { root: fileURLToPath(new URL('../..', import.meta.url)) },
});
