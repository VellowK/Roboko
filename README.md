# Roboko

> Version baseline: `2.0.1`

Roboko is an Android vocabulary-learning application built around authentic context, AI-assisted organization, and adaptive review. The product treats a learning item as a reusable entity: one item may contain multiple meaning branches, and each meaning may contain multiple contexts.

## Product direction

- **Context first:** review complete, readable contexts rather than isolated flashcards.
- **Meaning aware:** keep multiple meanings under one canonical word page and track each meaning independently.
- **Adaptive review:** mix new and review items in one queue; a daily target is guidance only, never a hard limit.
- **Low-friction AI:** support enrichment, semantic search, categorization, follow-up questions, and related-item discovery.
- **Quiet UX:** use a focused Material 3 interface without streak pressure or unnecessary gamification.

## Confirmed learning behavior

- Show one review item at a time.
- During recall, show the full context with the target expression masked and the part of speech visible.
- Do not show phonetics or provide target pronunciation before the answer.
- Hints reveal Chinese and English definitions inline or in a lightweight panel.
- After **Known** or **Unknown**, reveal the complete word card in place.
- Save completed answers immediately; unanswered cards do not count.
- Formal review has the strongest memory effect. Passive definition viewing records an event but does not restore progress.
- Meaning progress is bounded from `0.0` to `6.0`, non-linear, and subject to bounded natural forgetting.
- Contexts are sampled from the selected meaning branch, with avoidance of immediate repetition but no permanent exclusion.
- The completion screen shows new items, review count, total learning, progress change, and mastered count; it does not show streaks.

## Information architecture

The primary navigation contains **Home**, **Library**, **Review**, and **AI**. Settings are reached from the top bar or profile entry.

- **Home:** reference target, today's summary, recent additions, and the review CTA.
- **Library:** newest-first items, semantic search, AI categories, item details, and recycle bin.
- **Review:** context-first recall, optional hint, answer reveal, follow-up AI, and completion summary.
- **AI:** context-aware learning conversation that can add new learning items without changing the active review queue.

## Data and domain rules

- A canonical learning item owns multiple numbered meaning branches (`①`, `②`, `③`, ...).
- Each meaning branch has independent progress and multiple context entries.
- Every context, including non-representative contexts, can enter the review pool.
- AI categories are many-to-many organizational data only; they never alter review priority or memory progress.
- A deleted item requires confirmation, enters a seven-day recycle bin, remains searchable with a deleted indicator, and can be restored with its learning data. Restored items are categorized again; category relationships are not restored.
- Reliable word-root or affix decomposition may be shown as a visual aid, but uncertain decomposition must be omitted.

## Technology and architecture

The planned stack is **Kotlin + Jetpack Compose + Material 3**, with Navigation Compose, ViewModel, Coroutines/`StateFlow`, Room, DataStore, and Retrofit or Ktor where remote services are needed.

Use a single-activity Compose architecture with unidirectional state flow:

- UI renders immutable state and dispatches user events.
- ViewModels call use cases.
- Repositories isolate local and remote data sources.
- Compose screens must not access DAOs or HTTP clients directly.
- Keep a mock repository so the core learning flow can be demonstrated without a real AI service.
- Keep review parameters in domain/use-case/review-engine configuration, not scattered through UI code.

Suggested areas:

```text
ui/{home,library,review,word,ai,category,recyclebin,settings}
domain/{model,usecase,review,search,categorization}
data/{local,remote,repository}
core/{design,navigation,ui,util}
```

## MVP path

1. Add a learning item.
2. Generate or organize meanings and contexts.
3. Categorize it with AI.
4. Display it newest first in the library.
5. Review it using a masked full-context card.
6. Optionally use a hint and answer Known or Unknown.
7. Update meaning-level progress.
8. Reveal the complete word card.
9. Ask an AI follow-up.
10. Finish with the concise completion summary.

## Working and version policy

- `handoff.md` and `processing.md` are **sensitive local files**. They must remain outside Git tracking and must never be committed.
- `processing.md` is a mandatory audit log. Every AI or human contributor must append what it inspected, changed, decided, tested, and what remains. Never record credentials, tokens, or other secrets in it.
- Repository versioning starts at `2.0.0`.
- The patch counter increases by exactly `0.0.1` **only when a commit is made to GitHub**. A local edit without a GitHub commit does not increment the version.
- The current committed baseline is `2.0.1`.
- Commit messages must include the resulting version and a concise description of the change.

## Status

This repository currently contains the product baseline and implementation guidance. Android source implementation should follow the constraints in this README and the local handoff document.

## License

No license has been selected. All rights reserved unless a license is added later.
