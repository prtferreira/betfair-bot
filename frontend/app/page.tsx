import Link from "next/link";

export default function Home() {
  return (
    <main style={{ padding: "2rem" }}>
      <div style={{ display: "flex", gap: "1rem", flexWrap: "wrap" }}>
        <Link href="/games">Go to Games</Link>
        <Link href="/games/analytics">Go to Game Analytics</Link>
      </div>
    </main>
  );
}
