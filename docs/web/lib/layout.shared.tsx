import type { BaseLayoutProps } from 'fumadocs-ui/layouts/shared';

export function baseOptions(): BaseLayoutProps {
    return {
        nav: {
            title: (
                <span className="brand">
                    blobs<span> / docs</span>
                </span>
            ),
        },
        githubUrl: 'https://github.com/nonomnonom/blobs',
        themeSwitch: { mode: 'light-dark-system' },
        links: [
            { text: 'Guides', url: '/docs', active: 'url' },
            {
                text: 'Download app',
                url: 'https://github.com/nonomnonom/blobs/releases/latest',
                external: true,
            },
        ],
    };
}
