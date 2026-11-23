import { useId, useMemo } from "react";
import logo from "@/assets/nelsen-logo-transparent.png";

const W = 842;
const H = 595;
const C = {
  paper: "#F4EFE3",
  navy: "#1C2A44",
  ochre: "#C9A24A",
  ochreDeep: "#B8903A",
  brick: "#A84A38",
  brickDeep: "#8E3B2C",
  ink: "#2A221C",
  muted: "#6B5A4A",
  faint: "#8A7A66",
};

/** Names stored all-lowercase / all-caps get title-cased; mixed case is left alone. */
export function displayName(name: string) {
  const n = name.trim();
  if (!n) return "Your name";
  if (n !== n.toLowerCase() && n !== n.toUpperCase()) return n;
  return n.toLowerCase().replace(/(^|[\s'-])(\p{L})/gu, (_, a, b) => a + b.toUpperCase());
}

function guillocheD(d: number, steps = 1600) {
  const R = 100;
  const r = 37;
  const period = 2 * Math.PI * r;
  let out = "";
  for (let i = 0; i <= steps; i++) {
    const t = (period * i) / steps;
    const x = (R - r) * Math.cos(t) + d * Math.cos(((R - r) / r) * t);
    const y = (R - r) * Math.sin(t) - d * Math.sin(((R - r) / r) * t);
    out += `${i ? "L" : "M"}${x.toFixed(1)},${y.toFixed(1)}`;
  }
  return out;
}

function scallopD(r: number, n = 30) {
  let out = "";
  for (let i = 0; i < n * 8; i++) {
    const a = (2 * Math.PI * i) / (n * 8);
    const rr = r + 3 * Math.cos(n * a);
    out += `${i ? "L" : "M"}${(rr * Math.cos(a)).toFixed(1)},${(rr * Math.sin(a)).toFixed(1)}`;
  }
  return out + "Z";
}

const STAR = Array.from({ length: 10 }, (_, i) => {
  const a = -Math.PI / 2 + (i * Math.PI) / 5;
  const rr = i % 2 ? 5.5 : 13;
  return `${(rr * Math.cos(a)).toFixed(1)},${(rr * Math.sin(a)).toFixed(1)}`;
}).join(" ");

export function CertificateArt({
  learnerName,
  courseTitle,
  score,
  date,
  certificateId,
}: {
  learnerName: string;
  courseTitle?: string | undefined;
  score?: number | undefined;
  date?: string | undefined;
  certificateId?: string | undefined;
}) {
  const uid = useId().replace(/:/g, "");
  const guilloche = useMemo(() => [95, 80, 65, 50].map((d) => guillocheD(d)), []);
  const scallop = useMemo(() => scallopD(37), []);
  const name = displayName(learnerName);
  const title = courseTitle || "Learning track";
  const nameSize = Math.min(46, Math.floor(560 / (name.length * 0.47)));
  const titleSize = Math.min(25, Math.floor(620 / (title.length * 0.52)));
  const micro = "NELSEN SAVANNAH · VERIFIED CERTIFICATE · ".repeat(30);
  const serif = "var(--font-display), Georgia, serif";
  const sans = "var(--font-sans), Arial, sans-serif";

  return (
    <svg
      viewBox={`0 0 ${W} ${H}`}
      role="img"
      aria-label={`Certificate of completion for ${name}`}
      className="block h-auto w-full"
    >
      <defs>
        <clipPath id={`${uid}-page`}><rect width={W} height={H} /></clipPath>
        <clipPath id={`${uid}-inner`}><rect x="46" y="46" width={W - 92} height={H - 92} /></clipPath>
        <path id={`${uid}-mt`} d={`M52,52 H${W - 52} V${H - 52} H52 Z`} />
      </defs>
      <rect width={W} height={H} fill={C.paper} />

      <g clipPath={`url(#${uid}-page)`}>
        <g transform={`rotate(-24 ${W / 2} ${H / 2})`} fontFamily={sans} fontWeight="700" fontSize="9" letterSpacing="3" fill={C.brick} opacity="0.05">
          {Array.from({ length: 19 }).flatMap((_, row) =>
            Array.from({ length: 7 }).map((_, col) => (
              <text key={`${row}-${col}`} x={-300 + col * 190 + (row % 2 ? 95 : 0)} y={-240 + row * 52}>NELSEN SAVANNAH</text>
            )),
          )}
        </g>
      </g>

      <g clipPath={`url(#${uid}-inner)`}>
        <g transform={`translate(${W / 2} ${H / 2 - 20}) scale(2.25)`} fill="none" stroke={C.ochre} strokeWidth={0.3 / 2.25} opacity="0.3">
          {guilloche.map((d, i) => <path key={i} d={d} />)}
        </g>
      </g>

      <rect x="21" y="21" width={W - 42} height={H - 42} fill="none" stroke={C.navy} strokeWidth="2.2" />
      <rect x="28" y="28" width={W - 56} height={H - 56} fill="none" stroke={C.ochre} strokeWidth="0.9" />
      <rect x="45" y="45" width={W - 90} height={H - 90} fill="none" stroke={C.ochre} strokeWidth="0.6" />
      <text fontSize="3.4" letterSpacing="0.9" fill={C.navy} opacity="0.55" fontFamily="Arial, sans-serif">
        <textPath href={`#${uid}-mt`}>{micro}</textPath>
      </text>
      {[[45, 45], [W - 45, 45], [45, H - 45], [W - 45, H - 45]].map(([x, y]) => (
        <g key={`${x}-${y}`} transform={`translate(${x} ${y})`}>
          <rect x="-10" y="-10" width="20" height="20" fill={C.navy} transform="rotate(45)" />
          <rect x="-6" y="-6" width="12" height="12" fill="none" stroke={C.ochre} strokeWidth="0.9" transform="rotate(45)" />
          <circle r="1.9" fill={C.ochre} />
        </g>
      ))}

      <image href={logo} x={W / 2 - 80} y="72" width="160" height="23.6" preserveAspectRatio="xMidYMid meet" />

      <g textAnchor="middle">
        <text x={W / 2} y="186" fontFamily={sans} fontWeight="700" fontSize="10" letterSpacing="4.5" fill={C.brick}>CERTIFICATE OF COMPLETION</text>
        <g stroke={C.ochre} strokeWidth="0.75">
          <line x1={W / 2 - 128} y1="201" x2={W / 2 - 15} y2="201" />
          <line x1={W / 2 + 15} y1="201" x2={W / 2 + 128} y2="201" />
        </g>
        <rect x={W / 2 - 3} y="198" width="6" height="6" fill={C.ochre} transform={`rotate(45 ${W / 2} 201)`} />
        <text x={W / 2} y="234" fontFamily={serif} fontStyle="italic" fontSize="12" fill={C.muted}>This certifies that</text>
        <text x={W / 2} y="288" fontFamily={serif} fontStyle="italic" fontSize={nameSize} fill={C.navy}>{name}</text>
        <line x1={W / 2 - 195} y1="298" x2={W / 2 + 195} y2="298" stroke={C.ochre} strokeWidth="1" />
        <text x={W / 2} y="341" fontFamily={serif} fontStyle="italic" fontSize="12" fill={C.muted}>has successfully completed the learning track</text>
        <text x={W / 2} y={352 + titleSize * 0.85} fontFamily={serif} fontWeight="700" fontSize={titleSize} fill={C.ink}>{title}</text>
        {score != null ? (
          <text x={W / 2} y="399" fontFamily={sans} fontSize="10.5" fill={C.muted}>with an overall result of {score}%</text>
        ) : null}
      </g>

      <g transform={`translate(${W / 2} 482)`}>
        <polygon points="-19,13 -28,49 -15,42 -6,54 1,21" fill={C.brick} />
        <polygon points="19,13 28,49 15,42 6,54 -1,21" fill={C.brickDeep} />
        <path d={scallop} fill={C.ochre} />
        <circle r="31" fill="none" stroke={C.paper} strokeWidth="1" opacity="0.8" />
        <circle r="26" fill={C.ochreDeep} stroke={C.paper} strokeWidth="0.7" strokeOpacity="0.6" />
        <polygon points={STAR} fill={C.paper} />
      </g>

      <g fontFamily={sans}>
        <text x="157" y="475" fontSize="10" fill="#4A3B2E">{date || "—"}</text>
        <line x1="157" y1="481" x2="297" y2="481" stroke={C.ochre} strokeWidth="0.7" />
        <text x="157" y="496" fontSize="7.5" letterSpacing="1.2" fill={C.faint}>DATE ISSUED</text>
        <text x={W - 157} y="475" fontSize="10" fill="#4A3B2E" textAnchor="end">{certificateId || "—"}</text>
        <line x1={W - 297} y1="481" x2={W - 157} y2="481" stroke={C.ochre} strokeWidth="0.7" />
        <text x={W - 157} y="496" fontSize="7.5" letterSpacing="1.2" fill={C.faint} textAnchor="end">CERTIFICATE ID</text>
      </g>
    </svg>
  );
}
