import Link from 'next/link';

export default function Page404() {
  return (
    <main className={'error'}>
      <h1>404</h1>
      <p>
        Kunne ikke finne det du leter etter. Gå <Link href={'/'}>hjem</Link>.
      </p>
    </main>
  );
}
