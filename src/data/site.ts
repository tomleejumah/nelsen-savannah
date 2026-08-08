export const ORG = {
  name: "Nelsen Savannah",
  legal: "Nelsen Savannah Organization + Company Limited",
  tagline: "Every young person deserves a map, not a guess.",
  email: "hello@nelsensavanna.co.ke",
  phone: "+254 700 000 000",
  location: "Nairobi, Kenya",
};

export type Program = {
  slug: string;
  title: string;
  blurb: string;
  audience: string;
  tone: "brand" | "ember" | "maroon";
};

export const PROGRAMS: Program[] = [
  {
    slug: "sela-programme",
    title: "Sela programme",
    blurb: "Guided mentorship pathway for young people building clarity, confidence and next steps.",
    audience: "Mentees",
    tone: "brand",
  },
  {
    slug: "trailblazers",
    title: "Trailblazers",
    blurb: "For young leaders ready to stretch — peer cohorts, mentor access and real-world exposure.",
    audience: "Emerging leaders",
    tone: "ember",
  },
  {
    slug: "scripture-safari",
    title: "Scripture Safari",
    blurb: "Faith-rooted guidance that walks with mentees through life, purpose and community.",
    audience: "Faith & life",
    tone: "maroon",
  },
  {
    slug: "go-for-it-codelab",
    title: "Go for it Codelab",
    blurb: "Hands-on coding lab — projects, mentor feedback and skills you can show in a portfolio.",
    audience: "Aspiring builders",
    tone: "brand",
  },
];

export const PARTNERS = [
  {
    name: "Recruitment agencies",
    detail:
      "Vetted hiring partners across tech, finance, health, media and trades receive shortlists from our cohorts.",
  },
  {
    name: "Employer talent pipelines",
    detail:
      "Companies use us as an early pipeline — internships, attachments and graduate roles come to the cohort first.",
  },
  {
    name: "Placement follow-through",
    detail:
      "Your mentor stays with you through applications and the first months on the job, not just the introduction.",
  },
];

export const ROADMAP = [
  {
    phase: "Now",
    title: "The mentorship platform",
    body: "Programs, cohorts, events with seat reservations, media and mentor matching — live on this site.",
    status: "Live",
  },
  {
    phase: "Next",
    title: "Nelsen Connect App",
    body: "The companion Android app (rebranded from Nisisi Connect Hub): curated learning paths, real projects, milestones and direct mentor-to-mentee conversations.",
    status: "In design",
  },
  {
    phase: "Later",
    title: "Nelsen LMS",
    body: "A multi-tenant learning system for teachers, students and supervisors — competency grids, rubric-based marking, live progress dashboards and WCAG 2.2 AA accessibility built in.",
    status: "Planned",
  },
  {
    phase: "Later",
    title: "Gallery & courses library",
    body: "Photo and video archive of cohorts, plus an on-demand course library backed by live data.",
    status: "Planned",
  },
];

export const STATS = [
  { value: "4,200+", label: "Mentees guided" },
  { value: "380", label: "Active mentors" },
  { value: "62", label: "Career paths mapped" },
  { value: "91%", label: "Complete their cohort" },
];

export const REVIEWS = [
  {
    name: "Amina W.",
    role: "Mentee · Sela programme",
    quote:
      "I came in unsure where to start. Sela gave me a mentor who checks in every fortnight and a clearer map for the next year.",
  },
  {
    name: "Brian O.",
    role: "Mentee · Trailblazers",
    quote:
      "Trailblazers pushed me into rooms I would not have walked into alone. The peer cohort kept me honest.",
  },
  {
    name: "Dr. Faith K.",
    role: "Mentor · Go for it Codelab",
    quote:
      "I have mentored informally for years. Nelsen Savannah gave the relationship structure — goals, sessions, and a way to measure whether I was actually helping.",
  },
  {
    name: "Kevin M.",
    role: "Mentee · Scripture Safari",
    quote:
      "Scripture Safari became my board of directors. Faith talk, career talk, hard talk. Nothing performative.",
  },
  {
    name: "Sharon N.",
    role: "Mentee · Go for it Codelab",
    quote:
      "The Codelab sessions taught me to ship small projects and ask better questions. That combination changed my year.",
  },
  {
    name: "Tom A.",
    role: "Mentor · Trailblazers",
    quote:
      "It is the most professional youth programme I have volunteered with in Kenya — clear goals, real accountability.",
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
      "Situation, decision, action, result. A simple structure mentees use to stop rambling under pressure.",
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
    title: "What good mentorship actually looks like (it isn't advice)",
    description:
      "Most mentors talk too much. The best ones ask better questions and hold their mentee to the answer.",
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

export const LEARNING_TRACKS: LearningTrack[] = [
  {
    slug: "career-foundations",
    title: "Career Foundations",
    blurb:
      "Map your strengths to real professions, understand what each path demands, and build a first three-year plan.",
    level: "Foundation",
    modules: 8,
    hours: 10,
    tone: "brand",
  },
  {
    slug: "communication-mastery",
    title: "Communication Mastery",
    blurb:
      "Speaking, writing and presenting with control — with interview practice as one module inside the syllabus.",
    level: "Intermediate",
    modules: 10,
    hours: 14,
    tone: "ember",
  },
  {
    slug: "workplace-readiness",
    title: "Workplace Readiness",
    blurb:
      "Professional etiquette, feedback, email and meeting craft, and how to work well with a senior colleague.",
    level: "Intermediate",
    modules: 7,
    hours: 9,
    tone: "maroon",
  },
  {
    slug: "money-and-wellbeing",
    title: "Money & Wellbeing",
    blurb:
      "Budgeting a first salary, black tax, boundaries, peer pressure and looking after your mental health.",
    level: "Foundation",
    modules: 6,
    hours: 8,
    tone: "brand",
  },
  {
    slug: "mentor-certification",
    title: "Mentor Certification",
    blurb:
      "Listening frameworks, safeguarding, goal-setting and progress tracking for professionals giving back.",
    level: "Advanced",
    modules: 9,
    hours: 12,
    tone: "ember",
  },
  {
    slug: "digital-and-portfolio",
    title: "Digital Skills & Portfolio",
    blurb:
      "Practical digital tools plus a portfolio and CV you can actually send to a hiring partner.",
    level: "Intermediate",
    modules: 8,
    hours: 11,
    tone: "maroon",
  },
];

export const LMS_FEATURES = [
  {
    title: "Guided learning paths",
    detail: "Each track unlocks module by module, so mentees always know the next step.",
  },
  {
    title: "Mentor-marked assignments",
    detail: "Rubric-based feedback from a real mentor instead of an automated score.",
  },
  {
    title: "Competency grids",
    detail: "See exactly which skills are proven, in progress, or still untouched.",
  },
  {
    title: "Live progress dashboards",
    detail: "Mentees, mentors and supervisors share one honest view of progress.",
  },
  {
    title: "Certificates that hold up",
    detail: "Verifiable completion records our hiring partners can trust.",
  },
  {
    title: "Accessible by design",
    detail: "WCAG 2.2 AA targets, keyboard-first navigation and low-bandwidth mode.",
  },
];
