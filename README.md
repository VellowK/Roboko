# Roboko

Roboko is an Android vocabulary learning application built around real-world context, AI-assisted organization, and adaptive review.

A learning object is not merely a word. In Roboko, one learning item can contain multiple meaning branches, and every meaning can contain multiple authentic contexts. Review progress is tracked at the meaning level so that the application can distinguish what the learner actually remembers.

## Product Principles

- Learn from complete, readable context instead of isolated flashcards.
- Mix new and due material in one adaptive review queue.
- Treat the daily learning target as guidance, never as a hard queue limit.
- Keep multiple meanings under one canonical word page.
- Track each meaning branch independently on a `0.0` to `6.0` scale.
- Use AI to organize meanings, contexts, categories, and follow-up learning.
- Keep the interface quiet, focused, and free of unnecessary gamification.

## Core Experience

### Context-first review

Each review card shows the full saved context while masking the target expression. The part of speech remains visible, while phonetics and pronunciation stay hidden during recall. Learners can reveal a lightweight hint containing the Chinese and English definitions before answering.

After choosing **Known** or **Unknown**, Roboko expands the complete word card in place. The learner can inspect meanings and contexts or continue with an AI follow-up without leaving the review flow.

### Meaning-aware memory

A word can have several numbered meaning branches, each with its own contexts and review progress. Contexts jointly verify a meaning and are sampled with repetition avoidance. Natural forgetting follows a bounded curve, while active learning events and formal review influence future scheduling.

### AI-assisted library

Roboko uses AI to:

- normalize and enrich newly added learning items;
- attach new contexts to the correct meaning branch;
- select representative contexts;
- organize items into flexible, many-to-many categories;
- provide semantic search across words, definitions, translations, and contexts;
- support contextual follow-up questions and discovery of related items.

AI categories are for organization and retrieval only. They do not change review priority or memory progress.

### Recoverable deletion

Deleted learning items remain in a recycle bin for seven days. They stay searchable, can be restored with their learning history, and receive time-based forgetting decay while deleted. Restored items are categorized again from the current state.

## Technology

- Kotlin
- Jetpack Compose
- Material 3
- Navigation Compose
- ViewModel with Coroutines and `StateFlow`
- Room for local persistence
- DataStore for preferences
- Retrofit or Ktor Client for remote services

The intended architecture is a single-activity Compose application with unidirectional state flow and clear UI, domain, and data boundaries. Compose screens must not access DAOs or HTTP clients directly.

## Planned Application Areas

- **Home**: today's reference target, activity summary, recent additions, and review entry point.
- **Library**: recently added items, semantic search, AI categories, and recycle bin.
- **Review**: one context-first recall card at a time, hints, answer reveal, and session completion.
- **AI**: a general learning conversation that can inherit the current item and meaning context.
- **Settings**: preferences reached from the top app bar rather than permanent bottom navigation.

## MVP

The first complete product path is:

1. Add a learning item.
2. Generate or organize its meanings and contexts with AI.
3. Categorize it automatically.
4. Find it in the library.
5. Review it from a masked, full-context card.
6. Optionally use a hint and answer Known or Unknown.
7. Update meaning-level progress.
8. Reveal the complete word card.
9. Ask an AI follow-up.
10. Finish with a concise session summary.

## Status

Version `2.0.0` establishes the clean repository baseline and product specification. Android project implementation follows from this baseline.

## License

No license has been selected yet. All rights are reserved unless a license is added later.
