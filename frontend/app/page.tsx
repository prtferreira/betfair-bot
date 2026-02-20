import Link from "next/link";

export default function Home() {
  return (
    <main style={{ padding: "2rem" }}>
      <Link href="/games">Go to Games</Link>
    </main>
  );
}
