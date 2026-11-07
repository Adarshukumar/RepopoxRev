import "@fontsource/dm-sans/400.css";
import "@fontsource/dm-sans/500.css";
import "@fontsource/dm-sans/600.css";
import "@fontsource/dm-sans/700.css";
import "@fontsource/manrope/600.css";
import "@fontsource/manrope/700.css";
import "@fontsource/manrope/800.css";
import React, { useState, useEffect, useRef } from "react";
import { createRoot } from "react-dom/client";
import {
  House,
  Compass,
  Bookmark,
  MessageCircle,
  Bell,
  Search,
  Plus,
  ArrowUpRight,
  ArrowRight,
  Heart,
  Send,
  X,
  Sun,
  Moon,
  Image,
  MapPin,
  Check,
  SlidersHorizontal,
  ChevronRight,
  Menu,
  Grid2X2,
  Smile,
  Camera,
} from "lucide-react";
import "./style.css";
const people = [
  {
    name: "Sofia Chen",
    handle: "sofiawanders",
    initial: "SC",
    color: "#e6c0a6",
  },
  {
    name: "Noah Williams",
    handle: "noah.in.frames",
    initial: "NW",
    color: "#b4c7ba",
  },
  {
    name: "Amara Okafor",
    handle: "amarastudio",
    initial: "AO",
    color: "#d8bbd7",
  },
  { name: "Leo Martin", handle: "leomakes", initial: "LM", color: "#b7c5d5" },
];
const initialPosts = [
  {
    id: 1,
    person: 0,
    image: "coast",
    location: "Positano, Italy",
    category: "Travel",
    text: "Took the long way home. Would take it again.",
    tags: "#slowliving #italiansummer",
    likes: 248,
    comments: ["The colors are unreal!", "Adding this to my list."],
    time: "24 min",
  },
  {
    id: 2,
    person: 1,
    image: "lake",
    location: "Dolomites, Italy",
    category: "Outdoors",
    text: "No signal. No plans. Just a really good view.",
    tags: "#getoutside #littleescapes",
    likes: 186,
    comments: ["This is my kind of weekend."],
    time: "48 min",
  },
  {
    id: 3,
    person: 2,
    image: "cafe",
    location: "Copenhagen, Denmark",
    category: "Everyday",
    text: "Collecting quiet corners and very good coffee.",
    tags: "#theeveryday #coffeediaries",
    likes: 92,
    comments: [],
    time: "1 hour",
  },
  {
    id: 4,
    person: 3,
    image: "mountains",
    location: "Somewhere in the Alps",
    category: "Photography",
    text: "A reminder to look up a little more often.",
    tags: "#35mm #perspective",
    likes: 317,
    comments: [],
    time: "2 hours",
  },
];
function read(key, fallback) {
  try {
    return JSON.parse(localStorage.getItem(key)) ?? fallback;
  } catch {
    return fallback;
  }
}
const me = {
  name: "Alex Morgan",
  handle: "alexmorgan",
  initial: "AM",
  color: "#d9d6bd",
};
function Avatar({ person = me, size = 40 }) {
  return (
    <span
      className="avatar"
      style={{
        width: size,
        height: size,
        background: person.color,
        fontSize: size * 0.29,
      }}
    >
      {person.initial}
    </span>
  );
}
function App() {
  const [page, setPage] = useState("Home"),
    [tab, setTab] = useState("For you"),
    [category, setCategory] = useState("All interests"),
    [search, setSearch] = useState(""),
    [dark, setDark] = useState(() => read("offscript-dark", false)),
    [liked, setLiked] = useState(() => read("offscript-liked", [])),
    [saved, setSaved] = useState(() => read("offscript-saved", [])),
    [followed, setFollowed] = useState(() =>
      read("offscript-followed", [0, 1]),
    ),
    [posts, setPosts] = useState(() => read("offscript-posts", initialPosts)),
    [modal, setModal] = useState(null),
    [draft, setDraft] = useState(""),
    [attachment, setAttachment] = useState(null),
    [comment, setComment] = useState(""),
    [toast, setToast] = useState(""),
    [chat, setChat] = useState(0),
    [messages, setMessages] = useState(() => read("offscript-messages", {})),
    [message, setMessage] = useState("");
  const dialogRef = useRef(null),
    returnFocus = useRef(null);
  useEffect(() => {
    const handler = (e) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        document.querySelector('[aria-label="Search moments"]')?.focus();
      }
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, []);
  useEffect(() => {
    document.documentElement.dataset.theme = dark ? "dark" : "light";
    localStorage.setItem("offscript-dark", JSON.stringify(dark));
  }, [dark]);
  useEffect(() => {
    for (const [k, v] of Object.entries({
      liked,
      saved,
      followed,
      posts,
      messages,
    }))
      try {
        localStorage.setItem("offscript-" + k, JSON.stringify(v));
      } catch {
        setToast("Storage is full. This change is available for this session.");
      }
  }, [liked, saved, followed, posts, messages]);
  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(""), 3500);
    return () => clearTimeout(t);
  }, [toast]);
  useEffect(() => {
    if (modal) {
      returnFocus.current = document.activeElement;
      dialogRef.current?.showModal();
    } else {
      dialogRef.current?.close();
      returnFocus.current?.focus();
    }
  }, [modal]);
  const toggle = (set, id) =>
    set((old) =>
      old.includes(id) ? old.filter((x) => x !== id) : [...old, id],
    );
  const navigate = (p) => {
    setPage(p);
    setSearch("");
    window.scrollTo({ top: 0, behavior: "smooth" });
  };
  const visible = posts.filter(
    (p) =>
      (page !== "Saved" || saved.includes(p.id)) &&
      (page !== "Profile" || p.person === -1) &&
      (tab !== "Following" || page !== "Home" || followed.includes(p.person)) &&
      (category === "All interests" || category === p.category) &&
      (search === "" ||
        `${p.text} ${p.location} ${p.category} ${(people[p.person] || me).name}`
          .toLowerCase()
          .includes(search.toLowerCase())),
  );
  function publish(e) {
    e.preventDefault();
    if (!draft.trim()) return;
    setPosts([
      {
        id: Date.now(),
        person: -1,
        image: attachment,
        location: "A moment from my day",
        category: "Everyday",
        text: draft.trim(),
        tags: "",
        likes: 0,
        comments: [],
        time: "Just now",
      },
      ...posts,
    ]);
    setDraft("");
    setAttachment(null);
    setModal(null);
    setTab("For you");
    setCategory("All interests");
    navigate("Home");
    setToast("Your moment is now in the feed.");
  }
  function upload(e) {
    const f = e.target.files[0];
    if (!f) return;
    if (!f.type.startsWith("image/") || f.size > 3 * 1024 * 1024) {
      setToast("Choose an image under 3 MB.");
      return;
    }
    const r = new FileReader();
    r.onload = () => setAttachment(r.result);
    r.readAsDataURL(f);
  }
  const img = (s) => (s?.startsWith("data:") ? s : `/images/${s}.jpg`);
  function Post({ post }) {
    const person = people[post.person] || me;
    return (
      <article className="post">
        <div className="post-head">
          <button
            className="person-button"
            onClick={() => setModal({ type: "person", person })}
          >
            <Avatar person={person} />
            <span>
              <strong>
                {person.name}{" "}
                {post.person === 0 && <span className="verified">✦</span>}
              </strong>
              <small>
                {post.location} <span>· {post.time}</span>
              </small>
            </span>
          </button>
          <button
            className="icon-btn"
            aria-label={`Save post by ${person.name}`}
            onClick={() => toggle(setSaved, post.id)}
          >
            <Bookmark
              size={19}
              fill={saved.includes(post.id) ? "currentColor" : "none"}
            />
          </button>
        </div>
        {post.image && (
          <button
            className="post-photo"
            aria-label={`View photo: ${post.location}`}
            onClick={() => setModal({ type: "photo", post })}
          >
            <img
              src={img(post.image)}
              alt={post.location}
              loading={post.id > 2 ? "lazy" : "eager"}
            />
            <span className="photo-label">
              <MapPin size={12} />
              {post.location}
            </span>
            <span className="expand-photo">
              <ArrowUpRight size={19} />
            </span>
          </button>
        )}
        <div className="post-bottom">
          <div className="post-actions">
            <button
              className={liked.includes(post.id) ? "liked" : ""}
              aria-label={`Like post ${post.id}`}
              aria-pressed={liked.includes(post.id)}
              onClick={() => toggle(setLiked, post.id)}
            >
              <Heart
                size={21}
                fill={liked.includes(post.id) ? "currentColor" : "none"}
              />
              {post.likes + (liked.includes(post.id) ? 1 : 0)}
            </button>
            <button
              aria-label={`Comments on post ${post.id}`}
              onClick={() => {
                setComment("");
                setModal({ type: "comments", id: post.id });
              }}
            >
              <MessageCircle size={20} />
              {post.comments.length}
            </button>
            <button
              aria-label={`Share post ${post.id}`}
              onClick={() => {
                setModal({ type: "share", post });
              }}
            >
              <Send size={20} />
            </button>
            <span className="category-label">{post.category}</span>
          </div>
          <p>
            <strong>{person.handle}</strong> {post.text}
          </p>
          <span className="tags">{post.tags}</span>
        </div>
      </article>
    );
  }
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a
          className="wordmark"
          href="#"
          onClick={(e) => {
            e.preventDefault();
            navigate("Home");
          }}
        >
          offscript<span>✳</span>
        </a>
        <div className="nav-label">YOUR LITTLE CORNER</div>
        <nav>
          {[
            [House, "Home"],
            [Compass, "Explore"],
            [Bookmark, "Saved"],
            [MessageCircle, "Messages"],
            [Bell, "Notifications"],
          ].map(([Icon, name]) => (
            <button
              key={name}
              className={page === name ? "nav-item active" : "nav-item"}
              onClick={() => navigate(name)}
            >
              <Icon size={21} />
              <span>{name}</span>
              {name === "Messages" && <b className="badge">2</b>}
              {name === "Notifications" && <i />}
            </button>
          ))}
        </nav>
        <button
          className="primary create-btn"
          onClick={() => setModal({ type: "create" })}
        >
          <Plus size={20} />
          Create a post
        </button>
        <div className="sidebar-note">
          <span>
            LESS SCROLLING.
            <br />
            MORE CONNECTING.
          </span>
          <p>
            Life happens off script.
            <br />
            Bring a little of it here.
          </p>
          <span className="scribble">↗</span>
        </div>
        <div className="sidebar-bottom">
          <button className="theme-button" onClick={() => setDark(!dark)}>
            {dark ? <Sun size={18} /> : <Moon size={18} />}
            <span>{dark ? "Light appearance" : "Dark appearance"}</span>
          </button>
          <button className="my-profile" onClick={() => navigate("Profile")}>
            <Avatar />
            <span>
              <strong>Alex Morgan</strong>
              <small>@alexmorgan</small>
            </span>
            <ChevronRight size={17} />
          </button>
        </div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <span className="breadcrumb">
            Your world, a little closer <span>✦</span>
          </span>
          <a className="mobile-brand" href="#" onClick={() => navigate("Home")}>
            offscript✳
          </a>
          <label className="search">
            <Search size={17} />
            <input
              aria-label="Search moments"
              placeholder="Find your kind of people"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                if (!["Home", "Explore", "Saved", "Profile"].includes(page))
                  setPage("Explore");
              }}
            />
            <kbd>⌘ K</kbd>
          </label>
          <button
            className="icon-btn top-bell"
            aria-label="Notifications"
            onClick={() => navigate("Notifications")}
          >
            <Bell size={20} />
            <i />
          </button>
          <button
            className="icon-btn"
            aria-label="Your profile"
            onClick={() => navigate("Profile")}
          >
            <Avatar size={34} />
          </button>
        </header>
        <div className="content-layout">
          <main>
            <section className="page-heading">
              <div>
                <div className="eyebrow">
                  A LITTLE INSPIRATION. A LOT OF REAL LIFE.
                </div>
                <h1>
                  {page === "Home"
                    ? "Good things happen here."
                    : page === "Explore"
                      ? "Follow your curiosity."
                      : page === "Saved"
                        ? "Keep what moves you."
                        : page === "Messages"
                          ? "Keep the conversation going."
                          : page === "Notifications"
                            ? "You’re part of the story."
                            : "A life, in little moments."}
                </h1>
                <p>
                  {page === "Home"
                    ? "Fresh perspectives, familiar faces, and moments worth sharing."
                    : page === "Explore"
                      ? "A new place, a new perspective, a new connection."
                      : page === "Saved"
                        ? "Your own collection of little discoveries."
                        : page === "Profile"
                          ? "Alex Morgan · Finding the extraordinary in the everyday."
                          : "A little less distance between you and your people."}
                </p>
              </div>
              <span className="heading-star">✳</span>
            </section>
            {["Home", "Explore", "Saved", "Profile"].includes(page) && (
              <>
                {page === "Home" && (
                  <section className="stories" aria-label="Stories">
                    <button
                      className="story"
                      onClick={() => setModal({ type: "create" })}
                    >
                      <span className="story-add">
                        <Avatar size={62} />
                        <b>
                          <Plus size={14} />
                        </b>
                      </span>
                      <span>Your story</span>
                    </button>
                    {[
                      ...people,
                      { name: "Sunday club", initial: "☀", color: "#ead095" },
                      { name: "Out there", initial: "↗", color: "#b3c8b5" },
                    ].map((p, i) => (
                      <button
                        className="story"
                        key={p.name}
                        onClick={() =>
                          setModal({
                            type: "story",
                            person: p,
                            image: [
                              "italy",
                              "lake",
                              "cafe",
                              "mountains",
                              "coffee",
                              "coast",
                            ][i],
                          })
                        }
                      >
                        <span className="story-ring">
                          <span
                            style={{
                              backgroundImage: `url(${img(["italy", "lake", "cafe", "mountains", "coffee", "coast"][i])})`,
                            }}
                          />
                        </span>
                        <span>{p.name.split(" ")[0]}</span>
                        {i === 0 && <i className="live-dot" />}
                      </button>
                    ))}
                  </section>
                )}
                {page === "Profile" && (
                  <section className="profile-panel">
                    <Avatar size={80} />
                    <div>
                      <h2>Alex Morgan</h2>
                      <p>@alexmorgan</p>
                      <span>
                        {posts.filter((p) => p.person === -1).length} posts ·{" "}
                        {followed.length} following
                      </span>
                    </div>
                    <button
                      className="primary"
                      onClick={() => setModal({ type: "create" })}
                    >
                      Share a moment
                    </button>
                  </section>
                )}
                <div className="feed-tabs">
                  <div>
                    {(page === "Home"
                      ? ["For you", "Following"]
                      : [
                          "Explore" === page
                            ? "Discover"
                            : page === "Saved"
                              ? "Your collection"
                              : "Your moments",
                        ]
                    ).map((t) => (
                      <button
                        key={t}
                        className={
                          tab === t || page !== "Home" ? "selected" : ""
                        }
                        onClick={() => setTab(t)}
                      >
                        {t}
                        {t === "For you" && <span>✦</span>}
                      </button>
                    ))}
                  </div>
                  <span className="feed-count">
                    {visible.length} moments <SlidersHorizontal size={15} />
                  </span>
                </div>
                <div className="filter-row">
                  {[
                    "All interests",
                    "Photography",
                    "Travel",
                    "Everyday",
                    "Outdoors",
                  ].map((c) => (
                    <button
                      key={c}
                      onClick={() => setCategory(c)}
                      className={category === c ? "chip chosen" : "chip"}
                    >
                      {c}
                    </button>
                  ))}
                </div>
                <div
                  className={
                    "feed-grid " + (page === "Explore" ? "explore-grid" : "")
                  }
                >
                  {visible.map((post) => (
                    <Post key={post.id} post={post} />
                  ))}
                </div>
                {visible.length === 0 && (
                  <div className="empty">
                    <Bookmark size={30} />
                    <h2>
                      {page === "Saved"
                        ? "Make room for inspiration."
                        : "No moments here just yet."}
                    </h2>
                    <p>
                      {page === "Saved"
                        ? "Tap the bookmark on any post to keep it here."
                        : "Try a different interest, follow someone, or share your own."}
                    </p>
                    <button
                      className="primary"
                      onClick={() => {
                        setCategory("All interests");
                        setTab("For you");
                        navigate("Explore");
                      }}
                    >
                      Explore moments <ArrowRight size={16} />
                    </button>
                  </div>
                )}
                <div className="feed-end">
                  <span>✳</span> You’re all caught up. Go make a little life.
                </div>
              </>
            )}
            {page === "Messages" && (
              <section className="messages-panel">
                <div className="chat-list">
                  {people.map((p, i) => (
                    <button
                      key={p.name}
                      onClick={() => setChat(i)}
                      className={chat === i ? "current" : ""}
                    >
                      <Avatar person={p} />
                      <span>
                        <strong>{p.name}</strong>
                        <small>
                          {i === 0
                            ? "That place looks incredible!"
                            : "Let’s catch up soon."}
                        </small>
                      </span>
                    </button>
                  ))}
                </div>
                <div className="conversation">
                  <h3>
                    {people[chat].name}
                    <small>
                      Demo conversation · messages stay on this device
                    </small>
                  </h3>
                  <div className="bubbles">
                    <p>Hey Alex! What have you been exploring lately?</p>
                    {(messages[chat] || []).map((m, i) => (
                      <p className="outgoing" key={i}>
                        {m}
                      </p>
                    ))}
                  </div>
                  <form
                    onSubmit={(e) => {
                      e.preventDefault();
                      if (message.trim()) {
                        setMessages({
                          ...messages,
                          [chat]: [...(messages[chat] || []), message.trim()],
                        });
                        setMessage("");
                      }
                    }}
                  >
                    <input
                      aria-label="Message"
                      placeholder="Say something nice…"
                      value={message}
                      onChange={(e) => setMessage(e.target.value)}
                    />
                    <button
                      className="primary"
                      disabled={!message.trim()}
                      aria-label="Send message"
                    >
                      <Send size={18} />
                    </button>
                  </form>
                </div>
              </section>
            )}
            {page === "Notifications" && (
              <section className="notification-list">
                {people.slice(0, 3).map((p, i) => (
                  <button
                    key={p.name}
                    onClick={() => setModal({ type: "person", person: p })}
                  >
                    <Avatar person={p} />
                    <span>
                      <strong>{p.name}</strong>{" "}
                      {
                        [
                          "and 12 others liked your perspective.",
                          "started following you.",
                          "invited you to The Sunday Club.",
                        ][i]
                      }
                      <small>
                        {[12, 38, 54][i]} minutes ago · demo activity
                      </small>
                    </span>
                    <Heart size={20} />
                  </button>
                ))}
              </section>
            )}
          </main>
          <aside className="rightbar">
            <div className="daily-card">
              <span className="pill">
                <span /> THE DAILY PROMPT
              </span>
              <div className="daily-art">
                <div className="orange-disc" />
                <span className="art-spark">✳</span>
                <span className="art-line">↗</span>
              </div>
              <h2>
                Find beauty
                <br />
                in the ordinary.
              </h2>
              <p>
                Your morning light. A familiar street.
                <br />
                Show us a little moment from today.
              </p>
              <button
                onClick={() => {
                  setDraft("Today’s little moment: ");
                  setModal({ type: "create" });
                }}
              >
                Share your moment <ArrowUpRight size={18} />
              </button>
              <span className="prompt-number">NO. 028 / THE EVERYDAY</span>
            </div>
            <section className="suggestions">
              <div className="section-title">
                <h3>Your kind of people</h3>
                <span>✦</span>
              </div>
              <p>A few fresh perspectives to follow.</p>
              {people.slice(1).map((p, i) => (
                <div className="suggestion" key={p.name}>
                  <button
                    className="person-button"
                    onClick={() => setModal({ type: "person", person: p })}
                  >
                    <Avatar person={p} size={37} />
                    <span>
                      <strong>{p.name}</strong>
                      <small>@{p.handle}</small>
                    </span>
                  </button>
                  <button
                    aria-label={`${followed.includes(i + 1) ? "Unfollow" : "Follow"} ${p.name}`}
                    className={
                      "follow-btn " +
                      (followed.includes(i + 1) ? "is-followed" : "")
                    }
                    onClick={() => toggle(setFollowed, i + 1)}
                  >
                    {followed.includes(i + 1) ? (
                      <Check size={17} />
                    ) : (
                      <Plus size={17} />
                    )}
                  </button>
                </div>
              ))}
              <button
                className="text-button"
                onClick={() => {
                  navigate("Explore");
                  setToast("Discover new perspectives in Explore.");
                }}
              >
                Discover more people <ArrowRight size={15} />
              </button>
            </section>
            <section className="club-card">
              <div className="club-icon">☀</div>
              <span className="eyebrow">GOOD COMPANY, GREAT IDEAS</span>
              <h3>The Sunday Club</h3>
              <p>
                A slower corner of the internet.
                <br />
                For people who notice the little things.
              </p>
              <button onClick={() => setModal({ type: "club" })}>
                Come on in <ArrowUpRight size={16} />
              </button>
              <span className="club-members">
                ✦ &nbsp; 1.2k like-minded humans
              </span>
            </section>
            <footer>
              About · Community guidelines · Privacy
              <br />
              <span>© 2026 offscript · A frontend concept</span>
              <strong>Less algorithm. More you. ↗</strong>
            </footer>
          </aside>
        </div>
      </div>
      <nav className="mobile-nav">
        {[
          [House, "Home"],
          [Compass, "Explore"],
          [Plus, "Create"],
          [Bookmark, "Saved"],
          [MessageCircle, "Messages"],
        ].map(([Icon, p]) => (
          <button
            aria-label={p}
            className={page === p ? "active" : ""}
            key={p}
            onClick={() =>
              p === "Create" ? setModal({ type: "create" }) : navigate(p)
            }
          >
            <Icon size={22} />
            <span>{p}</span>
          </button>
        ))}
      </nav>
      <dialog
        ref={dialogRef}
        onCancel={() => setModal(null)}
        onClick={(e) => {
          if (e.target === dialogRef.current) setModal(null);
        }}
      >
        <button
          className="close-modal icon-btn"
          aria-label="Close dialog"
          onClick={() => setModal(null)}
        >
          <X size={21} />
        </button>
        {modal?.type === "create" && (
          <form className="composer" onSubmit={publish}>
            <div className="eyebrow">A LITTLE PIECE OF YOUR WORLD</div>
            <h2>Life, unfiltered.</h2>
            <div className="person-button">
              <Avatar />
              <span>
                <strong>Alex Morgan</strong>
                <small>Posting to your demo feed</small>
              </span>
            </div>
            <textarea
              autoFocus
              aria-label="Post caption"
              maxLength={600}
              placeholder="What’s your little moment today?"
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
            />
            {attachment && (
              <div className="attachment">
                <img src={img(attachment)} alt="Post attachment" />
                <button
                  type="button"
                  className="icon-btn"
                  aria-label="Remove photo"
                  onClick={() => setAttachment(null)}
                >
                  <X size={18} />
                </button>
              </div>
            )}
            <div className="composer-photos">
              <span>Try a photo</span>
              {["coast", "lake", "cafe"].map((im) => (
                <button
                  type="button"
                  aria-label={`Attach ${im} photo`}
                  key={im}
                  onClick={() => setAttachment(im)}
                >
                  <img src={img(im)} alt={im} />
                </button>
              ))}
            </div>
            <div className="composer-footer">
              <label className="upload">
                <Image size={20} />
                <span>Add photo</span>
                <input type="file" accept="image/*" onChange={upload} />
              </label>
              <small>{draft.length}/600</small>
              <button className="primary" disabled={!draft.trim()}>
                Share moment <ArrowUpRight size={17} />
              </button>
            </div>
          </form>
        )}
        {modal?.type === "comments" && (
          <div className="comments-modal">
            <h2>A little conversation.</h2>
            {posts
              .find((p) => p.id === modal.id)
              ?.comments.map((c, i) => (
                <div className="comment" key={i}>
                  <Avatar person={i % 2 ? me : people[1]} size={32} />
                  <p>{c}</p>
                </div>
              ))}
            <form
              onSubmit={(e) => {
                e.preventDefault();
                if (!comment.trim()) return;
                setPosts(
                  posts.map((p) =>
                    p.id === modal.id
                      ? { ...p, comments: [...p.comments, comment.trim()] }
                      : p,
                  ),
                );
                setComment("");
              }}
            >
              <input
                autoFocus
                aria-label="Comment"
                placeholder="Add a little kindness…"
                value={comment}
                onChange={(e) => setComment(e.target.value)}
              />
              <button className="primary" disabled={!comment.trim()}>
                <Send size={18} />
              </button>
            </form>
          </div>
        )}
        {["photo", "story"].includes(modal?.type) && (
          <div className="photo-modal">
            <img
              src={img(modal.post?.image || modal.image)}
              alt={modal.post?.location || `${modal.person.name}'s story`}
            />
            <h2>{modal.post?.location || `${modal.person.name}’s moment`}</h2>
            <p>{modal.post?.text || "A little glimpse of life, off script."}</p>
          </div>
        )}
        {modal?.type === "person" && (
          <div className="person-modal">
            <Avatar person={modal.person} size={90} />
            <h2>{modal.person.name}</h2>
            <p>@{modal.person.handle}</p>
            <p>
              Collecting moments, not things.
              <br />
              Photography · travel · the everyday
            </p>
            <button
              className="primary"
              onClick={() => {
                const i = people.findIndex((p) => p.name === modal.person.name);
                if (i < 0) {
                  setModal(null);
                  navigate("Profile");
                } else toggle(setFollowed, i);
              }}
            >
              {followed.includes(people.indexOf(modal.person))
                ? "Following ✓"
                : "Follow this perspective"}
            </button>
          </div>
        )}
        {modal?.type === "share" && (
          <div className="share-modal">
            <h2>Good things are better shared.</h2>
            <p>Send this moment to someone in your demo inbox.</p>
            {people.map((p, i) => (
              <button
                className="share-person"
                key={p.name}
                onClick={() => {
                  setMessages({
                    ...messages,
                    [i]: [
                      ...(messages[i] || []),
                      `A moment for you: ${modal.post.text} — ${modal.post.location}`,
                    ],
                  });
                  setChat(i);
                  navigate("Messages");
                  setModal(null);
                  setToast("Moment shared in your local conversation.");
                }}
              >
                <Avatar person={p} />
                {p.name}
                <Send size={17} />
              </button>
            ))}
          </div>
        )}
        {modal?.type === "club" && (
          <div className="person-modal">
            <div className="club-icon">☀</div>
            <h2>The Sunday Club</h2>
            <p>Slow mornings. Small discoveries. Good company.</p>
            <p>
              This is a frontend community preview. For now, explore the
              Everyday collection and add your own moment.
            </p>
            <button
              className="primary"
              onClick={() => {
                setModal(null);
                navigate("Explore");
                setCategory("Everyday");
              }}
            >
              Explore the everyday <ArrowRight size={16} />
            </button>
          </div>
        )}
      </dialog>
      {toast && (
        <div className="toast" role="status">
          <Check size={18} />
          {toast}
        </div>
      )}
    </div>
  );
}
createRoot(document.getElementById("root")).render(<App />);
