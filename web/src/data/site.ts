export const ORG = {
  name: "Nelsen Savannah",
  legal: "Nelsen Savannah Organization + Company Limited",
  hubName: "Nelsen Savannah Innovation Hub",
  tagline: "Learn, create, code, build — and launch what matters.",
  vision:
    "An accessible innovation ecosystem where people learn, communicate, create, code, build, understand emerging technologies, and transform ideas into practical solutions.",
  email: "hello@nelsensavanna.co.ke",
  website: "https://nelsav.com",
  location: "Nairobi, Kenya",
  websiteLegacy: "https://www.nisisi.africa",
};

export const FACILITATORS = [
  { name: "Tomee Juma", role: "Lead facilitator" },
  { name: "Evans Nyairo", role: "Lead facilitator" },
] as const;

export const FIRST_INTAKE = {
  eventId: "evt-intake-aug-2026",
  label: "Friday 29 August 2026",
  dateMs: Date.parse("2026-08-29T09:00:00+03:00"),
} as const;

export const CORE_VALUES = [
  { key: "EXPLORE", detail: "Discover opportunities and emerging technologies." },
  { key: "COMMUNICATE", detail: "Express ideas clearly and collaborate effectively." },
  { key: "CREATE", detail: "Turn imagination into meaningful work." },
  { key: "CODE", detail: "Use technology to solve problems." },
  { key: "BUILD", detail: "Develop practical products, prototypes, and solutions." },
  { key: "UNDERSTAND", detail: "Use knowledge, evidence, and data to make better decisions." },
  {
    key: "INNOVATE & LAUNCH",
    detail: "Transform validated ideas into solutions, ventures, and impact.",
  },
] as const;

export const INNOVATION_CYCLE = [
  "Identify",
  "Research",
  "Ideate",
  "Design",
  "Build",
  "Test",
  "Improve",
  "Present",
  "Launch",
] as const;

export const LEARNING_MODEL =
  "Programmes move learners from exposure and foundational skills toward practical projects, collaboration, innovation, and real-world application.";

export const APPROACH =
  "Participants communicate ideas, create outputs, code solutions, build practical projects, understand evidence and technology, collaborate with others, and develop solutions with measurable value.";

export type Program = {
  slug: string;
  title: string;
  subtitle: string;
  blurb: string;
  audience: string;
  tone: "brand" | "ember" | "maroon";
  topics: string[];
};

export const PROGRAMS: Program[] = [
  {
    slug: "future-safari",
    title: "Future Safari",
    subtitle: "Future skills and innovation",
    blurb:
      "Entry pathway for digital literacy, emerging tech awareness, design thinking, and innovation challenges.",
    audience: "Explorers",
    tone: "brand",
    topics: [
      "Digital literacy & AI awareness",
      "Introduction to coding & robotics",
      "Design thinking & innovation challenges",
      "Future careers & entrepreneurship fundamentals",
    ],
  },
  {
    slug: "savannah-robotics-automation-lab",
    title: "Savannah Robotics & Automation Lab",
    subtitle: "Robotics, IoT and engineering",
    blurb:
      "Hands-on robotics, electronics, microcontrollers, sensors, and automation — from builds to competitions.",
    audience: "Engineering",
    tone: "ember",
    topics: [
      "Arduino, sensors & actuators",
      "Robotics programming & motor control",
      "Internet of Things & embedded systems",
      "Robot design, assembly & engineering challenges",
    ],
  },
  {
    slug: "savannah-data-ai-academy",
    title: "Savannah Data & AI Academy",
    subtitle: "Data science and artificial intelligence",
    blurb:
      "From data literacy and visualization to Python, generative AI, and responsible AI capstone projects.",
    audience: "Data & AI",
    tone: "maroon",
    topics: [
      "Excel, statistics & Power BI",
      "Python for data analysis",
      "Generative AI & prompt engineering",
      "Machine learning fundamentals & data storytelling",
    ],
  },
  {
    slug: "savannah-creative-lab",
    title: "Savannah Creative Lab",
    subtitle: "Design, media and digital creativity",
    blurb:
      "Visual communication, UI/UX, photography, video, motion, and portfolio-ready creative output.",
    audience: "Creative",
    tone: "brand",
    topics: [
      "Graphic design & branding",
      "UI/UX, wireframing & prototyping",
      "Photography & video production",
      "Motion graphics & digital storytelling",
    ],
  },
  {
    slug: "savannah-software-engineering-lab",
    title: "Savannah Software Engineering Lab",
    subtitle: "Coding and software development",
    blurb:
      "Full-stack foundations — HTML, JavaScript, Python, databases, React, mobile, and collaborative projects.",
    audience: "Developers",
    tone: "ember",
    topics: [
      "Programming fundamentals & Git",
      "Front-end, back-end & APIs",
      "Databases, SQL & cloud deployment",
      "Cybersecurity basics & AI-assisted development",
    ],
  },
  {
    slug: "savannah-sauti-academy",
    title: "Savannah Sauti Academy",
    subtitle: "Communication and leadership",
    blurb:
      "Public speaking, storytelling, negotiation, emotional intelligence, and professional communication.",
    audience: "Leaders",
    tone: "maroon",
    topics: [
      "Public speaking & presentation skills",
      "Debate, active listening & team communication",
      "Negotiation & conflict resolution",
      "Interview skills, personal branding & networking",
    ],
  },
  {
    slug: "kijiji-hub",
    title: "Kijiji Hub",
    subtitle: "Innovation, entrepreneurship and problem-solving",
    blurb:
      "Community innovation from problem identification through MVPs, business models, pitches, and demo days.",
    audience: "Entrepreneurs",
    tone: "brand",
    topics: [
      "Design thinking & ideation",
      "Prototype development & MVPs",
      "Market research & business models",
      "Pitch development, hackathons & demo days",
    ],
  },
];

export const PRACTICAL_APPLICATION = [
  "Robotics builds and sensor-based systems",
  "Smart-device and automation prototypes",
  "Technology demonstrations and team challenges",
  "Data and AI capstone projects",
  "Creative portfolios and software projects",
  "Community innovation and startup pitches",
];

export const ROADMAP = [
  {
    phase: "Live",
    title: "Innovation Hub programmes",
    body: "Seven connected pathways — Future Safari through Kijiji Hub — with cohort intakes and facilitator-led sessions.",
    status: "Live",
  },
  {
    phase: "Live",
    title: "Learning platform",
    body: "Tracks, modules, lessons, progress, coursework, and school wings on web and Android with one account.",
    status: "Live",
  },
  {
    phase: "Next",
    title: "Cohort milestones & quizzes",
    body: "Scheduled releases per intake, mentor-authored quizzes, and demo checkout before live payment rails.",
    status: "Building",
  },
  {
    phase: "Later",
    title: "Certificates & partner schools",
    body: "Verifiable certificates, richer analytics, and expanded partner school catalogues.",
    status: "Planned",
  },
];

export const STATS = [
  { value: "7", label: "Core programmes" },
  { value: "Nairobi", label: "Innovation Hub" },
  { value: "9 steps", label: "Innovation cycle" },
  { value: "App & web", label: "Learn anywhere" },
];

export const OUTCOMES = [
  {
    title: "Beyond passive learning",
    body: "Learners communicate, create, code, and build — not just watch slides.",
  },
  {
    title: "Connected pathways",
    body: "Enter through exploration and grow specialist technical, creative, and entrepreneurial skills.",
  },
  {
    title: "Real-world application",
    body: "Projects, prototypes, portfolios, and pitches with measurable value.",
  },
  {
    title: "Evidence-led decisions",
    body: "Data literacy and responsible AI sit alongside hands-on engineering and design.",
  },
];

export const MEDIA = [
  {
    postID: "beyond-the-big-four-careers",
    publisherID: "nelsen",
    title: "Beyond the big four: 20 careers Kenyan teens are never shown",
    description:
      "Medicine, law, engineering, teaching. Here is what sits in the gap — and what each path actually pays, demands, and rewards.",
    fileType: "article",
    fileName: "beyond-the-big-four.md",
    mediaUrl: "",
    thumbnailUrl: "",
    timestamp: Date.parse("2026-07-28T12:00:00+03:00"),
    readMinutes: 7,
  },
  {
    postID: "interview-answers-that-land",
    publisherID: "nelsen",
    title: "The four-sentence answer that wins interviews",
    description:
      "Situation, decision, action, result. A simple structure learners use to stop rambling under pressure.",
    fileType: "article",
    fileName: "interview-answers.md",
    mediaUrl: "",
    thumbnailUrl: "",
    timestamp: Date.parse("2026-07-12T12:00:00+03:00"),
    readMinutes: 5,
  },
  {
    postID: "what-good-mentorship-looks-like",
    publisherID: "nelsen",
    title: "What good facilitation actually looks like",
    description:
      "The best programme leaders ask better questions and hold learners to the work they said they would ship.",
    fileType: "article",
    fileName: "good-mentorship.md",
    mediaUrl: "",
    thumbnailUrl: "",
    timestamp: Date.parse("2026-06-30T12:00:00+03:00"),
    readMinutes: 6,
  },
  {
    postID: "first-job-money-habits",
    publisherID: "nelsen",
    title: "Your first salary: the three habits that decide the next decade",
    description:
      "Black tax, lifestyle creep, and the 20-minute monthly ritual that keeps a junior salary from disappearing.",
    fileType: "article",
    fileName: "first-salary.md",
    mediaUrl: "",
    thumbnailUrl: "",
    timestamp: Date.parse("2026-06-18T12:00:00+03:00"),
    readMinutes: 8,
  },
];

/** @deprecated use MEDIA */
export const BLOGS = MEDIA;

export type LearningTrack = {
  slug: string;
  title: string;
  blurb: string;
  level: "Foundation" | "Intermediate" | "Advanced";
  modules: number;
  hours: number;
  tone: "brand" | "ember" | "maroon";
};

/** Catalogue labels aligned to Innovation Hub programme names (LMS tracks may differ). */
export const LEARNING_TRACKS: LearningTrack[] = PROGRAMS.map((p, i) => ({
  slug: p.slug,
  title: p.title,
  blurb: p.blurb,
  level: i === 0 || i === 6 ? "Foundation" : i >= 4 ? "Advanced" : "Intermediate",
  modules: 6 + (i % 3),
  hours: 8 + (i % 4) * 2,
  tone: p.tone,
}));

export const LMS_FEATURES = [
  {
    title: "Seven connected pathways",
    detail:
      "Future Safari through Kijiji Hub — learners enter through exploration and grow specialist capabilities.",
  },
  {
    title: "Skills you can prove",
    detail:
      "Tracks map to programme competencies so learners, tutors, and school admins see what is mastered vs still open.",
  },
  {
    title: "Vast learning materials",
    detail:
      "Modules, videos, readings, quizzes, and assignments across school wings — not a thin course list.",
  },
  {
    title: "Facilitator-marked coursework",
    detail:
      "Rubrics and feedback from programme facilitators instead of automated scores alone.",
  },
  {
    title: "Progress that follows you",
    detail:
      "Enrollments and lesson progress stay with your signed-in account when you switch schools or devices.",
  },
  {
    title: "Certificates schools trust",
    detail:
      "Verifiable completion records for school wings and partners — not a paper printout.",
  },
];
