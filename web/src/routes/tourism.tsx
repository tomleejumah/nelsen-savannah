import { createFileRoute, Link } from "@tanstack/react-router";
import { ArrowRight, Binoculars, Palmtree, Sun } from "lucide-react";

import { DeskInquiryForm } from "@/components/site/DeskInquiryForm";
import {
  Carousel,
  CarouselContent,
  CarouselItem,
  CarouselNext,
  CarouselPrevious,
} from "@/components/ui/carousel";

export const Route = createFileRoute("/tourism")({
  head: () => ({
    meta: [
      { title: "Tourism — Kenya Safaris & Coast | Nelsen Savannah" },
      {
        name: "description",
        content:
          "Kenya tourism desk — Maasai Mara, Amboseli, Lake Nakuru, and coast trips for travellers who want the journey itself.",
      },
      { property: "og:title", content: "Tourism | Nelsen Savannah" },
    ],
  }),
  component: TourismPage,
});

/** Kenya-only destinations — images from Touring Agency Kenya assets + Mara landscape */
const DESTINATIONS = [
  {
    id: "maasai-mara",
    name: "Maasai Mara",
    region: "Narok",
    blurb: "Open plains and the great wildebeest migration season.",
    image: "/tourism/kenya/maasai-mara.jpg",
  },
  {
    id: "amboseli",
    name: "Amboseli",
    region: "Kajiado",
    blurb: "Elephants and big skies at the foot of Kilimanjaro.",
    image: "/tourism/kenya/amboseli-park.jpg",
  },
  {
    id: "nakuru",
    name: "Lake Nakuru",
    region: "Rift Valley",
    blurb: "Flamingo shores and Rift Valley sanctuary country.",
    image: "/tourism/kenya/nakuru-flamingos.jpg",
  },
  {
    id: "mara-migration",
    name: "Mara migration herds",
    region: "Maasai Mara",
    blurb: "Wildebeest on the move across Mara grasslands.",
    image: "/tourism/kenya/mara-wildebeest.jpg",
  },
  {
    id: "safari-days",
    name: "Kenya safari days",
    region: "Parks circuit",
    blurb: "Game drives with local vehicles and guides.",
    image: "/tourism/kenya/kenya-vehicle.jpg",
  },
  {
    id: "wildlife",
    name: "Kenya wildlife",
    region: "National parks",
    blurb: "Big cats and birds that define a Kenya safari.",
    image: "/tourism/kenya/lion-kenya.jpg",
  },
] as const;

const OFFERS = [
  {
    icon: Binoculars,
    eyebrow: "Safari",
    title: "Parks & wildlife",
    body: "Maasai Mara, Amboseli, Nakuru, and classic Kenya park circuits.",
    image: "/tourism/kenya/kenya-safari.jpg",
  },
  {
    icon: Sun,
    eyebrow: "Migration",
    title: "Great migration season",
    body: "Time your trip for wildebeest herds on the Mara plains.",
    image: "/tourism/kenya/mara-wildebeest.jpg",
  },
  {
    icon: Palmtree,
    eyebrow: "Stay",
    title: "Camps & lodges",
    body: "Bush camps and lodge stays matched to your pace and budget.",
    image: "/tourism/kenya/ashnil-mara.png",
  },
] as const;

const GALLERY = [
  { src: "/tourism/kenya/lion-kenya.jpg", alt: "Lion in Kenya" },
  { src: "/tourism/kenya/leopard-kenya.jpg", alt: "Leopard in Kenya" },
  { src: "/tourism/kenya/amboseli.jpg", alt: "Amboseli National Park" },
  { src: "/tourism/kenya/kenya-cranes.jpg", alt: "Crowned cranes in Kenya" },
  { src: "/tourism/kenya/nakuru-flamingos.jpg", alt: "Flamingos at Lake Nakuru" },
  { src: "/tourism/kenya/maasai-mara.jpg", alt: "Maasai Mara landscape" },
  { src: "/tourism/kenya/kenya-vehicle.jpg", alt: "Kenya safari vehicle" },
  { src: "/tourism/kenya/lion-kenya-tour.jpg", alt: "Lion on Kenya safari" },
] as const;

function TourismPage() {
  return (
    <div className="pb-24">
      <section className="relative overflow-hidden px-5 pb-20 pt-36 sm:px-8 sm:pt-44">
        <div
          aria-hidden
          className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_at_top,oklch(0.52_0.21_25_/_0.1),transparent_55%)]"
        />
        <div className="relative mx-auto max-w-4xl">
          <p className="eyebrow text-ember">Tourism</p>
          <h1 className="mt-4 font-display text-4xl font-bold text-foreground sm:text-6xl">
            Kenya for the journey.
          </h1>
          <p className="mt-6 max-w-2xl text-base leading-relaxed text-muted-foreground sm:text-lg">
            Safaris and stays across Kenya’s parks and plains — Maasai Mara, Amboseli, Lake Nakuru,
            and more. Built for travellers who want the trip itself.
          </p>
          <div className="mt-10 flex flex-wrap gap-3">
            <a
              href="#inquire"
              className="inline-flex items-center gap-2 rounded-full bg-ember-gradient px-6 py-3 font-display text-sm font-semibold text-maroon-foreground shadow-ember-glow transition-transform hover:-translate-y-0.5"
            >
              Plan a trip <ArrowRight className="h-4 w-4" />
            </a>
            <a
              href="#destinations"
              className="inline-flex items-center gap-2 rounded-full border border-border bg-card px-6 py-3 font-display text-sm font-semibold text-foreground transition-colors hover:bg-accent"
            >
              Browse destinations
            </a>
          </div>
        </div>
      </section>

      <section id="destinations" className="mx-auto max-w-7xl scroll-mt-28 px-5 sm:px-8">
        <p className="eyebrow text-muted-foreground">Destinations</p>
        <h2 className="mt-3 font-display text-3xl font-bold">Where we take people in Kenya</h2>
        <div className="mt-10">
          <Carousel opts={{ align: "start", loop: true }} className="w-full">
            <CarouselContent className="-ml-3 sm:-ml-4">
              {DESTINATIONS.map((site) => (
                <CarouselItem
                  key={site.id}
                  className="basis-[85%] pl-3 sm:basis-1/2 sm:pl-4 lg:basis-1/3"
                >
                  <article className="overflow-hidden rounded-3xl border border-border/70 bg-card">
                    <div className="aspect-[4/3] overflow-hidden">
                      <img
                        src={site.image}
                        alt={site.name}
                        className="h-full w-full object-cover"
                        loading="lazy"
                      />
                    </div>
                    <div className="space-y-2 p-5">
                      <p className="text-xs font-semibold uppercase tracking-wide text-ember">
                        {site.region}
                      </p>
                      <h3 className="font-display text-xl font-semibold">{site.name}</h3>
                      <p className="text-sm leading-relaxed text-muted-foreground">{site.blurb}</p>
                    </div>
                  </article>
                </CarouselItem>
              ))}
            </CarouselContent>
            <CarouselPrevious className="left-2 hidden sm:flex" />
            <CarouselNext className="right-2 hidden sm:flex" />
          </Carousel>
        </div>
      </section>

      <section className="mx-auto mt-20 max-w-7xl px-5 sm:px-8">
        <p className="eyebrow text-muted-foreground">Ways to travel</p>
        <h2 className="mt-3 font-display text-3xl font-bold">Safari, migration, stay</h2>
        <ul className="mt-10 grid gap-6 md:grid-cols-3">
          {OFFERS.map((item) => (
            <li
              key={item.eyebrow}
              className="overflow-hidden rounded-3xl border border-border/70 bg-card"
            >
              <div className="aspect-[3/2] overflow-hidden">
                <img
                  src={item.image}
                  alt={item.title}
                  className="h-full w-full object-cover"
                  loading="lazy"
                />
              </div>
              <div className="space-y-2 p-5">
                <p className="text-xs font-semibold uppercase tracking-wide text-ember">
                  {item.eyebrow}
                </p>
                <h3 className="font-display text-lg font-semibold">{item.title}</h3>
                <p className="text-sm leading-relaxed text-muted-foreground">{item.body}</p>
              </div>
            </li>
          ))}
        </ul>
      </section>

      <section className="mx-auto mt-20 max-w-7xl px-5 sm:px-8">
        <div className="grid gap-0 overflow-hidden rounded-[2rem] border border-border/60 lg:grid-cols-2">
          <div className="bg-card/50 p-8 sm:p-12">
            <p className="eyebrow text-ember">For travellers</p>
            <h2 className="mt-4 font-display text-2xl font-bold sm:text-3xl">
              Your Kenya. Your pace.
            </h2>
            <p className="mt-4 text-sm leading-relaxed text-muted-foreground">
              Couples, families, and small groups. Tell us dates and whether you want Mara first,
              Amboseli, Nakuru, or a mixed circuit — we build the route.
            </p>
            <a
              href="#inquire"
              className="mt-8 inline-flex items-center gap-2 rounded-full bg-ember-gradient px-5 py-2.5 font-display text-sm font-semibold text-maroon-foreground"
            >
              Plan a trip <ArrowRight className="h-4 w-4" />
            </a>
          </div>
          <div className="bg-hero-gradient p-8 text-on-dark sm:p-12">
            <p className="eyebrow text-ember">For lodges & hosts</p>
            <h2 className="mt-4 font-display text-2xl font-bold sm:text-3xl">
              Host travellers we send your way.
            </h2>
            <p className="mt-4 text-sm leading-relaxed text-on-dark/80">
              If you run a Kenya lodge, camp, or cultural stay, share availability and rates. We
              place guests who already want Kenya.
            </p>
            <a
              href="#inquire"
              className="mt-8 inline-flex items-center gap-2 rounded-full bg-background/15 px-5 py-2.5 font-display text-sm font-semibold text-on-dark ring-1 ring-on-dark/30 transition-colors hover:bg-background/25"
            >
              Partner with us <ArrowRight className="h-4 w-4" />
            </a>
          </div>
        </div>
      </section>

      <section className="mx-auto mt-20 max-w-7xl px-5 sm:px-8">
        <p className="eyebrow text-muted-foreground">Gallery</p>
        <h2 className="mt-3 font-display text-3xl font-bold">Kenya on the ground</h2>
        <div className="mt-10">
          <Carousel opts={{ align: "start", loop: true }} className="w-full">
            <CarouselContent className="-ml-3 sm:-ml-4">
              {GALLERY.map((shot) => (
                <CarouselItem
                  key={shot.src}
                  className="basis-[85%] pl-3 sm:basis-1/2 sm:pl-4 lg:basis-1/3"
                >
                  <div className="aspect-[4/3] overflow-hidden rounded-3xl border border-border/70">
                    <img
                      src={shot.src}
                      alt={shot.alt}
                      className="h-full w-full object-cover"
                      loading="lazy"
                    />
                  </div>
                </CarouselItem>
              ))}
            </CarouselContent>
            <CarouselPrevious className="left-2 hidden sm:flex" />
            <CarouselNext className="right-2 hidden sm:flex" />
          </Carousel>
        </div>
      </section>

      <div className="mt-20">
        <DeskInquiryForm
          desk="tourism"
          title="Plan a Kenya trip"
          blurb="Pick dates with the calendar — we email the desk so we can shape the route together."
          subjectPrefix="Tourism inquiry"
          submitLabel="Send trip inquiry"
          fields={[
            {
              name: "name",
              label: "Full name",
              placeholder: "Your full name",
              required: true,
            },
            {
              name: "email",
              label: "Email",
              type: "email",
              placeholder: "you@email.com",
              required: true,
            },
            {
              name: "role",
              label: "I am",
              type: "select",
              required: true,
              options: [
                { value: "Traveller", label: "Traveller / guest" },
                { value: "Lodge / host", label: "Lodge / host partner" },
                { value: "Group organizer", label: "Group organizer" },
              ],
            },
            {
              name: "destinations",
              label: "Destinations of interest",
              type: "select",
              required: true,
              options: [
                { value: "Maasai Mara", label: "Maasai Mara" },
                { value: "Amboseli", label: "Amboseli" },
                { value: "Lake Nakuru", label: "Lake Nakuru" },
                { value: "Mixed Kenya circuit", label: "Mixed Kenya circuit" },
                { value: "Open to suggestions", label: "Open to suggestions" },
              ],
            },
            {
              name: "travelers",
              label: "Number of travellers",
              placeholder: "e.g. 2 adults",
              showWhen: {
                field: "role",
                values: ["Traveller", "Group organizer"],
              },
            },
            {
              name: "dateStart",
              label: "Travel from",
              type: "date",
              required: true,
              showWhen: {
                field: "role",
                values: ["Traveller", "Group organizer"],
              },
            },
            {
              name: "dateEnd",
              label: "Travel until",
              type: "date",
              required: true,
              showWhen: {
                field: "role",
                values: ["Traveller", "Group organizer"],
              },
            },
            {
              name: "message",
              label: "Anything else",
              type: "textarea",
              required: false,
              rows: 3,
              placeholder: "Optional notes for the desk…",
            },
          ]}
        />
      </div>

      <section className="mx-auto mt-10 max-w-7xl px-5 sm:px-8">
        <p className="text-sm text-muted-foreground">
          Looking for land or farm capital instead?{" "}
          <Link to="/invest" className="font-semibold text-maroon hover:underline">
            See invest
          </Link>
          .
        </p>
      </section>
    </div>
  );
}
