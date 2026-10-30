import type { Metadata } from 'next';
import localFont from 'next/font/local';
import type { ReactNode } from 'react';
import { Provider } from '@/components/provider';
import './global.css';

const bodyFont = localFont({
    src: [
        {
            path: '../../../android/app/src/main/res/font/dm_sans_regular.ttf',
            weight: '400',
        },
        {
            path: '../../../android/app/src/main/res/font/dm_sans_medium.ttf',
            weight: '500',
        },
        {
            path: '../../../android/app/src/main/res/font/dm_sans_semibold.ttf',
            weight: '600 800',
        },
    ],
    variable: '--font-body',
    display: 'swap',
});

const headingFont = localFont({
    src: '../../../android/app/src/main/res/font/nunito.ttf',
    variable: '--font-heading',
    display: 'swap',
});

export const metadata: Metadata = {
    title: {
        default: 'Blobs — Guides & documentation',
        template: '%s · Blobs',
    },
    description:
        'Set up Blobs, choose an offline model, explore your library, and build from source. Guides for the Android app compatible with GrapheneOS.',
    applicationName: 'Blobs',
    alternates: {
        types: {
            'text/plain': `${process.env.NEXT_PUBLIC_BASE_PATH || ''}/llms.txt`,
        },
    },
};

export default function Layout({ children }: { children: ReactNode }) {
    return (
        <html
            lang="en"
            suppressHydrationWarning
            className={`${bodyFont.variable} ${headingFont.variable}`}
        >
            <body className="flex min-h-screen flex-col">
                <Provider>{children}</Provider>
            </body>
        </html>
    );
}
