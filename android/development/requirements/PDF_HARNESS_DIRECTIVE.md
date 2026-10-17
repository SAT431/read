# Development Instructions: AI-Driven PDF Engineering and Harness-Based Study Architecture

## 1. First, We Need a Disciplined Development Approach

Okay, here's the thing: we need to approach this project properly.

Right now, you keep writing code, but there isn't much noticeable or measurable progress. Instead of continuously adding code without a clear implementation strategy, we need to develop the application in a disciplined, structured, and systematic way.

Every feature should be properly engineered, integrated, tested, and completed before moving on to the next major feature.

We should also make the core architecture flexible enough that future features can be added without constantly restructuring the entire application.

## 2. First Priority: Engineering the PDF Upload and Management System

Let's begin by properly implementing the PDF upload functionality.

Since this is an AI-powered application with an AI backend, uploading a PDF should not simply display the document. It should initiate a structured workflow.

When I upload a PDF, the application should:

- Accept the PDF and save it properly.
- Store its metadata and maintain a persistent record of the document.
- Allow me to rename, delete, organize, and manage uploaded PDFs.
- Preserve the original document for future reference.
- Make the uploaded document accessible to the AI backend.
- Track processing status and clearly communicate what has been completed.

These are only examples. I expect you to implement all the essential functionality that a production-ready document management system should have, even if I don't explicitly mention every individual feature.

## 3. What Should Happen When I Click "Let's Study"?

Once I have uploaded a PDF, suppose I click the **"Let's Study"** button.

At this point, the AI should activate and open an appropriate interactive interface.

The AI should analyze the uploaded PDF, understand its structure, identify its contents, and determine what study material is available.

A dialogue interface should appear where the AI can communicate with me through visible text.

Behind the scenes, the AI should perform a comprehensive analysis of the document.

For example, it should identify:

- Chapters, topics, and subtopics.
- Headings and subheadings.
- Paragraphs and their relationships.
- Diagrams, illustrations, tables, and captions.
- Page numbers and the locations of specific content.
- The logical flow and hierarchy of the educational material.

The AI should map everything into a structured representation so that it understands not just what content exists, but also where it exists and how different parts are connected.

Suppose I say:

**"Today, I want to study these specific topics."**

The AI should understand my request, identify the corresponding content in the uploaded PDF, and select the relevant pages, paragraphs, diagrams, and other materials.

It should then initiate the PDF engineering process for the selected content.

## 4. PDF Engineering: Much More Than OCR

This is one of the most important parts of the entire application.

I don't want a basic PDF reader that simply performs OCR and displays extracted text.

I want an advanced, AI-powered PDF engineering system.

The system should use an LLM, preferably with multimodal capabilities, to genuinely understand the document's content, layout, structure, and educational context.

For text, it should accurately extract the original paragraphs, recognize their context, and understand how they relate to surrounding content.

For images, especially NCERT Biology diagrams, the system should use appropriate programmatic extraction techniques, PDF processing libraries, image tools, and coding utilities.

Think of this engineering process as something similar to a coding harness used in vibe coding, where the AI has access to multiple tools and can choose the appropriate tool depending on the task.

For example, it may need to:

- Extract exact text and preserve its connection to the original PDF.
- Identify and crop diagrams accurately.
- Separate diagrams from surrounding text when required.
- Preserve labels, captions, and relationships between images and paragraphs.
- Analyze complicated page layouts using multimodal AI.
- Reconstruct structured educational content from the source material.
- Validate the extracted information against the original document.
- Store the engineered output in a structured format that the reading engine can understand.

The AI should not be limited to one extraction method. It should intelligently choose and combine suitable tools to achieve the best results.

Most importantly, the engineering process should preserve the accuracy of the original NCERT content.

## 5. Transform the Engineered PDF Into a Beautiful Reading Experience

After the PDF engineering process is complete, the extracted and structured content should be passed to our custom-built reading engine.

Remember, we are developing the reading engine ourselves, which means we have complete control over its components, layouts, rendering behavior, interactions, and visual presentation.

The goal is not simply to reproduce a PDF page.

The goal is to create a beautiful, highly readable, interactive, and intelligently structured learning experience.

The reading engine should be capable of displaying:

- Original paragraphs.
- Structured headings and subheadings.
- Beautifully formatted points and lists.
- Tables and comparisons.
- Diagrams and illustrations.
- Contextual explanations.
- Interactive educational components.
- Different combinations of these elements.

Everything should be aesthetically pleasing, professionally engineered, responsive, and optimized for a tablet-first study experience.

At the same time, the original NCERT content must remain accessible and accurately linked to its source.

## 6. AI-Controlled Content Reconstruction

Another important requirement is that the AI should be able to dynamically modify how content is presented.

For example, suppose I'm reading an NCERT paragraph and say:

**"I don't like how these NCERT lines are presented. Reconstruct this content into clear, well-organized points."**

The AI should understand the request and use the capabilities of our reading engine to reorganize that content.

Similarly, I might say:

- "Convert this paragraph into a table."
- "Make this explanation easier to understand."
- "Organize this topic using headings and subheadings."
- "Show the original NCERT lines."
- "Compare these two concepts."
- "Include the relevant diagram beside the explanation."
- "Make this content more visually appealing."

The AI should be able to perform these tasks using the components and functions we have already developed.

The original source must remain preserved, while the reconstructed presentation can change according to my request.

I don't want the application to rely on a fixed set of study layouts. The AI should intelligently select and arrange the appropriate components based on the current task.

## 7. Major Architectural Change: Build the Entire Application Like an AI Harness

This is a fundamental requirement, and I want you to make the necessary structural changes to the application's core architecture.

I want the application to work conceptually like an AI-powered coding harness, but instead of vibe coding, we are building a **vibe studying environment**.

Think about how coding agents can understand a project, access tools, inspect files, invoke functions, and complete complex tasks.

I want a similar level of control inside our study application.

### Every Feature Should Be a Controllable Block

Every function, UI component, feature, and meaningful action we implement should have a unique identity and a clearly defined interface.

For example:

- PDF upload and management.
- Document analysis.
- Topic selection.
- Paragraph rendering.
- Diagram rendering.
- Table generation.
- Dialogue interfaces.
- Quiz components.
- Reading controls.
- Content transformations.
- Navigation and progress tracking.

These should not be isolated features that the AI cannot interact with.

Instead, they should be modular, reusable blocks that the AI can discover, understand, invoke, and orchestrate.

Each block should clearly define:

- Its unique identifier.
- Its purpose and capabilities.
- The inputs it accepts.
- The outputs it produces.
- Its available actions.
- Its current state.
- Its dependencies.
- How other blocks can interact with it.
- How the AI can control it safely.

The AI should understand the available capabilities of the entire application.

When I assign a task, it should know which components and functions exist, how to use them, and how to coordinate them to achieve my request.

### AI Orchestration Instead of Fixed UI Behavior

The application should have an intelligent orchestration layer.

This layer should interpret my requests, understand the current application state, identify available capabilities, and decide what actions to perform.

For example, if I'm studying and ask the AI to generate a quiz, it should know how to activate the quiz components.

If I pause that quiz and ask to review a paragraph, it should temporarily shift the interface toward reading while preserving the quiz state.

Once I return, the quiz should resume without losing my progress.

The UI should adapt naturally to the task.

All actual UI components, animations, gestures, and interaction behaviors should be prebuilt by us. The AI should orchestrate and configure these existing capabilities rather than generating arbitrary frontend code at runtime.

This distinction is important because we want flexibility without sacrificing performance, reliability, or maintainability.

## 8. Make Future Features Extremely Easy to Integrate

The architecture should be designed for long-term extensibility.

Whenever we create a new feature in the future, it should be easy to integrate into the existing application.

Ideally, adding a new feature should involve creating a new modular capability, registering it with the system, defining its actions and interfaces, and making it discoverable by the AI orchestrator.

We should not have to rewrite large parts of the application every time we add something new.

The system should also make it easy to update AI instructions, tool definitions, prompts, component capabilities, and orchestration behavior.

I want the AI to have a clear, structured understanding of what the application can do, rather than relying on massive, confusing prompts that manually describe every function.

Design the architecture so that the AI can discover and use available capabilities through well-defined interfaces and a structured capability registry.

## 9. Don't Limit Implementation to Only What I Explicitly Mention

This is another important instruction.

When I describe a feature, I may explain only its main purpose or give a few examples.

That does not mean you should implement only those examples.

I expect you to think like an experienced software architect and engineer.

For instance, if I ask you to implement PDF uploading, you should also consider everything naturally associated with a complete PDF management system, such as renaming, deleting, storage management, validation, error handling, progress indication, duplicate handling, and persistence.

Apply this principle to every feature.

Identify missing requirements, fill the gaps intelligently, and implement complete workflows instead of disconnected demonstrations.

Every feature should be designed with production readiness in mind, including reliability, error recovery, performance, extensibility, and a polished user experience.

## 10. Immediate Development Priorities

For now, I want you to focus on two major tasks.

**Priority 1: PDF Engineering System**

Properly implement the complete workflow from PDF uploading and management to AI analysis, topic mapping, content selection, multimodal extraction, structured reconstruction, and integration with the reading engine.

The feature should be genuinely functional and testable, not merely a visual demonstration.

**Priority 2: Core Application Architecture**

Restructure the application's foundation into a modular, AI-controllable, harness-inspired architecture.

Every meaningful function and UI capability should have a unique identity and a defined interface so that the AI can understand, access, and orchestrate it.

Future capabilities should integrate naturally into this system without requiring major architectural changes.

## 11. Final Expectation

I want us to move away from continuously generating code without visible progress.

Work in disciplined implementation stages, with clear objectives, functional milestones, and proper testing.

Before implementing a major component, understand how it fits into the overall architecture.

After implementing it, verify that it actually works and integrates with the rest of the application.

Most importantly, remember the broader vision:

**We are not building an ordinary PDF reader or a conventional AI study application.**

We are building a deeply integrated, AI-orchestrated, harness-inspired studying environment where the AI understands the available application capabilities, dynamically coordinates them, and adapts the entire study experience to my needs.

The application should feel as flexible and powerful as working with an intelligent coding agent, except that its purpose is to help me study, understand, explore, practice, and interact with educational material.

Build the foundation properly now so that increasingly advanced features can be added naturally in the future.

**For the current implementation phase, concentrate on the PDF engineering workflow and the core modular AI-harness architecture. Make both reliable, extensible, aesthetically polished, and genuinely functional before expanding into other major features.**
