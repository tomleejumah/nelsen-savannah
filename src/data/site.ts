export const ORG = {
  name: "Nelsen Savanna",
  legal: "Nelsen Savanna Organization + Company Limited",
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
    slug: "career-compass",
    title: "Career Compass",
    blurb:
      "Path-mapping for teens and campus students — beyond doctor, lawyer, engineer. Exposure to 60+ real professions.",
    audience: "Ages 15–22",
    tone: "brand",
  },
  {
    slug: "communication-lab",
    title: "Communication Lab",
    blurb:
      "Speak, write and present with control. Public speaking, workplace communication and personal branding — with interview practice as one module of the syllabus.",
    audience: "Job seekers & interns",
    tone: "ember",
  },
  {
    slug: "social-life-and-wellbeing",
    title: "Social Life & Wellbeing",
    blurb:
      "Money habits, peer pressure, relationships, and mental health — the parts of growing up nobody teaches formally.",
    audience: "Teens & young adults",
    tone: "maroon",
  },
  {
    slug: "juniors-meet-seniors",
    title: "Juniors Meet Seniors",
    blurb:
      "Structured workplace mentorship pairing junior staff with senior professionals in their industry for 6 months.",
    audience: "0–5 years experience",
    tone: "brand",
  },
  {
    slug: "mentor-academy",
    title: "Mentor Academy",
    blurb:
      "Training and certification for mentors — listening frameworks, safeguarding, and goal-tracking that actually sticks.",
    audience: "Professionals giving back",
    tone: "ember",
  },
  {
    slug: "savanna-circles",
    title: "Savanna Circles",
    blurb:
      "Small monthly cohorts of 8 mentees and 2 mentors. Same room, same goals, real accountability.",
    audience: "All members",
    tone: "maroon",
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
    body: "Programs, cohorts, events with seat reservations, blogs and mentor matching — live on this site.",
    status: "Live",
  },
  {
    phase: "Next",
    title: "Nelsen Connect App",
    body: "A digital learning and empowerment hub: curated learning paths, real projects, milestones and direct mentor-to-mentee conversations.",
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
    role: "Mentee · Career Compass",
    quote:
      "I came in thinking my only options were medicine or law. I left with a shortlist of five careers I never knew existed — and a mentor who checks in every fortnight.",
  },
  {
    name: "Brian O.",
    role: "Mentee · Communication Lab",
    quote:
      "The mock panel was brutal in the best way. Three weeks later I walked into a real interview calm, and I got the offer.",
  },
  {
    name: "Dr. Faith K.",
    role: "Mentor · Juniors Meet Seniors",
    quote:
      "I have mentored informally for years. Nelsen Savanna gave the relationship structure — goals, sessions, and a way to measure whether I was actually helping.",
  },
  {
    name: "Kevin M.",
    role: "Mentee · Savanna Circles",
    quote:
      "The circle became my board of directors. Money talk, career talk, hard talk. Nothing performative.",
  },
  {
    name: "Sharon N.",
    role: "Mentee · Social Life & Wellbeing",
    quote:
      "The wellbeing sessions taught me boundaries and budgeting in the same month. That combination changed my year.",
  },
  {
    name: "Tom A.",
    role: "Mentor · Mentor Academy",
    quote:
      "The safeguarding training alone is worth it. It is the most professional youth programme I have volunteered with in Kenya.",
  },
];

export const BLOGS = [
  {
    slug: "beyond-the-big-four-careers",
    title: "Beyond the big four: 20 careers Kenyan teens are never shown",
    excerpt:
      "Medicine, law, engineering, teaching. Here is what sits in the gap — and what each path actually pays, demands, and rewards.",
    category: "Career Paths",
    date: "2026-07-28",
    readMinutes: 7,
  },
  {
    slug: "interview-answers-that-land",
    title: "The four-sentence answer that wins interviews",
    excerpt:
      "Situation, decision, action, result. A simple structure our Communication Lab mentees use to stop rambling under pressure.",
    category: "Communication",
    date: "2026-07-12",
    readMinutes: 5,
  },
  {
    slug: "what-good-mentorship-looks-like",
    title: "What good mentorship actually looks like (it isn't advice)",
    excerpt:
      "Most mentors talk too much. The best ones ask better questions and hold their mentee to the answer.",
    category: "Mentorship",
    date: "2026-06-30",
    readMinutes: 6,
  },
  {
    slug: "first-job-money-habits",
    title: "Your first salary: the three habits that decide the next decade",
    excerpt:
      "Black tax, lifestyle creep, and the 20-minute monthly ritual that keeps a junior salary from disappearing.",
    category: "Wellbeing",
    date: "2026-06-18",
    readMinutes: 8,
  },
];