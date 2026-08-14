export const ORG = {
  name: "Nelsen Savannah",
  legal: "Nelsen Savannah Organization + Company Limited",
  tagline: "Every young person deserves a map, not a guess.",
  email: "hello@nelsensavanna.co.ke",
  phone: "+254 ",
  phoneAlt: "+254 ",
  whatsapp: "254 ",
  location: "Nairobi, Kenya",
  websiteLegacy: "https://www.nisisi.africa",
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
    blurb:
      "Guided mentorship pathway for young people building clarity, confidence and next steps.",
    audience: "Mentees",
    tone: "brand",
  },
  {
    slug: "trailblazers",
    title: "Trailblazers",
    blurb:
      "For young leaders ready to stretch — peer cohorts, mentor access and real-world exposure.",
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
    blurb:
      "Hands-on coding lab — projects, mentor feedback and skills you can show in a portfolio.",
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
    title: "Mentorship site + companion app",
    body: "Programs, events, blogs, and mentor matching on this site — with the Android app for day-to-day mentorship.",
    status: "Live",
  },
  {
    phase: "Next",
    title: "Shared LMS API",
    body: "Tracks, progress %, and assignments on one API so mentees learn on Android and the web with the same account.",
    status: "Building",
  },
  {
    phase: "Later",
    title: "Certificates & mentor marking",
    body: "Quizzes, mentor-marked work, and certificates once a track hits the pass threshold.",
    status: "Planned",
  },
  {
    phase: "Later",
    title: "Gallery & course library",
    body: "Cohort photos and an on-demand library backed by live LMS data.",
    status: "Planned",
  },
];

export const STATS = [
  { value: "4", label: "Core programmes" },
  { value: "Nairobi", label: "Home base" },
  { value: "App & web", label: "Learn anywhere" },
  { value: "login", label: "Progress follows you" },
];

export const REVIEWS = [
  {
    name: "Mentee · Sela",
    role: "Cohort note",
    quote:
      "Having a named mentor and a written 90-day aim made the difference — not another motivational talk.",
  },
  {
    name: "Mentee · Trailblazers",
    role: "Cohort note",
    quote:
      "The peer circle forced me to send the outreach messages I kept postponing. Accountability over vibes.",
  },
  {
    name: "Mentor · Codelab",
    role: "Volunteer note",
    quote:
      "Structure helps: goals, sessions, and a clear way to see whether the mentee actually shipped something.",
  },
  {
    name: "Parent · Scripture Safari",
    role: "Programme note",
    quote:
      "Friday Safari days mix faith, mentorship, and play — kids come home tired in the best way.",
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
    title: "Many schools, one login",
    detail:
      "Partner schools run their own wings — catalogs, mentors, and cohorts — under one Nelsen Savannah account.",
  },
  {
    title: "Skills you can prove",
    detail:
      "Tracks map to real competencies so learners, tutors, and school admins see what’s mastered vs still open.",
  },
  {
    title: "Vast learning materials",
    detail:
      "Modules, videos, readings, quizzes, and assignments across schools — not a thin course list.",
  },
  {
    title: "Mentor-marked coursework",
    detail:
      "Rubric feedback from a real mentor or tutor instead of an automated score alone.",
  },
  {
    title: "Progress that follows you",
    detail:
      "Enrollments and lesson % stay with your signed-in account when you switch schools or devices.",
  },
  {
    title: "Certificates schools trust",
    detail:
      "Verifiable completion records for school wings and hiring partners — not a paper printout.",
  },
];
