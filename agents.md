# OrcPub Agent Instructions

## Overview

This document provides instructions for AI agents (Claude, GitHub Copilot, etc.) working on the OrcPub codebase.

## Codebase Understanding

Before making changes, please review the `CODEBASE.md` file for an overview of:
- Project structure and architecture
- Key technologies and frameworks used
- Coding conventions and patterns
- Important directories and files

## General Guidelines

### Code Style & Quality
- Follow existing code patterns and conventions in the repository
- Maintain consistency with the current codebase style
- Write clear, self-documenting code
- Add comments only where the logic isn't self-evident

### Technology Stack
- This is a ClojureScript project
- Uses Reagent (React wrapper for ClojureScript)
- Check `project.clj` for dependencies and build configuration
- Review existing code patterns before introducing new approaches

### Making Changes
1. **Read First**: Always read relevant files before making changes
2. **Understand Context**: Review `CODEBASE.md` for architectural context
3. **Minimal Changes**: Only change what's necessary for the task
4. **Test**: Ensure changes don't break existing functionality
5. **Follow Patterns**: Match the existing code style and structure

### File Organization
- `src/` - Main source code directory
- `test/` - Test files
- `resources/` - Static resources
- `dev/` - Development utilities
- Check `CODEBASE.md` for detailed structure information

### Before You Start
1. Review `CODEBASE.md` for codebase overview
2. Check existing similar implementations for patterns
3. Understand the context of your changes
4. Keep changes focused and minimal

### Security & Best Practices
- Avoid introducing security vulnerabilities
- Don't add unnecessary abstractions or over-engineer solutions
- Respect existing architecture decisions
- Ask for clarification if requirements are unclear

## Getting Help

- Review `README.md` for project setup and development instructions
- Check `CODEBASE.md` for architecture and structure details
- Look for similar existing code patterns to follow
- Consult the project maintainers for architectural decisions
