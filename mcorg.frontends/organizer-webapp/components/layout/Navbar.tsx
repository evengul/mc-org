import Link from 'next/link';

export default function Navbar() {
  return (
    <nav>
      <h1>
        <Link href={'/'}>MC-ORG</Link>
      </h1>
      <ul>
        <li>
          <Link href={'/'}>Hjem</Link>
        </li>
        <li>
          <Link href={'/project'}>Prosjekter</Link>
        </li>
          <li>
              <Link href={'/resources'}>Ressurser</Link>
          </li>
      </ul>
    </nav>
  );
}
