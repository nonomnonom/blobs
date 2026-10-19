import Image from 'next/image';
import Link from 'next/link';
import { HomeLayout } from 'fumadocs-ui/layouts/home';
import { baseOptions } from '@/lib/layout.shared';
import { HomeContainer } from '@/components/home-container';
import day from '@/public/media/welcome.webp';
import night from '@/public/media/welcome-night.webp';
import reading from '@/public/media/reading.webp';

const guides = [
    {
        title: 'Make yourself at home',
        description:
            'Install the app and get ready for your first offline chat.',
        href: '/docs/get-started',
        label: 'Start here',
    },
    {
        title: 'Find your next read',
        description:
            'Choose knowledge to keep, from travel guides to practical living.',
        href: '/docs/library',
        label: 'Your library',
    },
    {
        title: 'Follow the sources',
        description:
            'Ask a question, open a citation, and read the original passage.',
        href: '/docs/chat',
        label: 'Using Blobs',
    },
];

export default function Home() {
    const options = baseOptions();
    return (
        <HomeLayout
            {...options}
            slots={{ ...options.slots, container: HomeContainer }}
        >
            <main id="main-content" className="home-main">
                <section className="welcome" aria-labelledby="welcome-title">
                    <div className="welcome-copy">
                        <p className="eyebrow">A little help along the way</p>
                        <h1 id="welcome-title">
                            Your guide
                            <br />
                            to Blobs.
                        </h1>
                        <p className="welcome-description">
                            A world of knowledge, right on your phone. Get set
                            up, find something useful, and make it yours.
                        </p>
                        <Link href="/docs/get-started" className="primary-link">
                            Let’s get started <span aria-hidden="true">→</span>
                        </Link>
                        <p className="welcome-note">
                            For Android. Compatible with GrapheneOS.
                        </p>
                    </div>
                    <div className="welcome-art">
                        <Image
                            src={day}
                            alt="Blobs reading among lavender hills, a blue arch, and a little yellow star."
                            className="scene-day"
                            priority
                            sizes="(max-width: 700px) 100vw, 500px"
                        />
                        <div className="scene-night night-world">
                            <Image
                                src={night}
                                alt=""
                                sizes="(max-width: 700px) 100vw, 500px"
                            />
                            <Image
                                src={reading}
                                alt="Blobs reading under a starry night sky."
                                className="night-reader"
                                sizes="(max-width: 700px) 70vw, 350px"
                            />
                        </div>
                    </div>
                </section>
                <section
                    className="guide-section"
                    aria-labelledby="guide-title"
                >
                    <h2 id="guide-title">What would you like to do?</h2>
                    <div className="guide-list">
                        {guides.map((guide) => (
                            <Link
                                href={guide.href}
                                key={guide.href}
                                className="guide-link"
                            >
                                <span className="guide-label">
                                    {guide.label}
                                </span>
                                <span className="guide-content">
                                    <strong>{guide.title}</strong>
                                    <span>{guide.description}</span>
                                </span>
                                <span
                                    className="guide-arrow"
                                    aria-hidden="true"
                                >
                                    ↗
                                </span>
                            </Link>
                        ))}
                    </div>
                </section>
                <aside className="build-note">
                    <div>
                        <h2>Curious about the inside?</h2>
                        <p>
                            Blobs is open source. Explore how it works or help
                            make it better.
                        </p>
                    </div>
                    <Link href="/docs/developers">
                        For developers <span aria-hidden="true">→</span>
                    </Link>
                </aside>
            </main>
            <footer className="site-footer">
                <span>Blobs. A curious little companion.</span>
                <a href="https://github.com/nonomnonom/blobs/blob/main/NOTICE">
                    Licenses & artwork
                </a>
            </footer>
        </HomeLayout>
    );
}
