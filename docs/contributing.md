# Contributing Guide

Thank you for your interest in contributing to Dungeon Master's Vault! This guide will help you get started with contributing code, documentation, or other improvements to the project.

## 🎯 How You Can Contribute

### For New Contributors
- **Bug Reports**: Help us find and fix issues
- **Documentation**: Improve guides and examples
- **Testing**: Try new features and report problems
- **Code Review**: Review pull requests from other contributors

### For Experienced Developers
- **Bug Fixes**: Solve existing issues
- **New Features**: Add D&D content or application features
- **Performance**: Optimize code and database queries
- **Architecture**: Improve code structure and patterns

### For D&D Experts
- **Content Accuracy**: Verify D&D rules implementation
- **Game Balance**: Ensure rules work correctly together
- **New Content**: Add support for new D&D books and content

## 🚀 Getting Started

### 1. Set Up Your Development Environment

Follow our [Getting Started](getting-started.md) guide to set up your local development environment. Make sure you can:
- Run the application locally
- Execute tests successfully
- Make a simple change and see it work

### 2. Find an Issue to Work On

**Good First Issues:**
Look for issues labeled `good-first-issue` on our [GitHub Issues](https://github.com/codeGlaze/orcpub/issues) page. These are specifically chosen to be approachable for new contributors.

**Categories of Work:**
- **Bug fixes** - Issues labeled `bug`
- **Feature requests** - Issues labeled `enhancement` 
- **Documentation** - Issues labeled `documentation`
- **D&D content** - Issues labeled `content`
- **Performance** - Issues labeled `performance`

**Before Starting Work:**
1. Comment on the issue saying you'd like to work on it
2. Wait for a maintainer to assign it to you
3. Ask questions if anything is unclear

### 3. Fork and Branch

```bash
# Fork the repository on GitHub, then clone your fork
git clone https://github.com/YOUR_USERNAME/orcpub.git
cd orcpub

# Add upstream remote
git remote add upstream https://github.com/codeGlaze/orcpub.git

# Create a feature branch from develop
git checkout develop
git pull upstream develop
git checkout -b feature/descriptive-name

# Example branch names:
# feature/add-warlock-spells
# bugfix/character-sheet-pdf-formatting
# docs/improve-getting-started-guide
```

## 📝 Development Guidelines

### Code Style

#### Clojure/ClojureScript Standards
```clojure
;; Use descriptive function names
(defn calculate-spell-attack-bonus [character]
  ;; Implementation here
  )

;; Use threading macros for data transformation
(-> character
    (get :abilities)
    (get :intelligence)
    ability-modifier)

;; Use meaningful variable names
(let [intelligence-score (get-in character [:abilities :intelligence])
      intelligence-modifier (ability-modifier intelligence-score)]
  (+ base-spell-attack intelligence-modifier))

;; Document public functions
(defn calculate-armor-class
  "Calculates total AC from base AC, dexterity modifier, and bonuses.
   
   Args:
     base-ac: Base armor class from armor
     dex-mod: Dexterity modifier (may be capped by armor)
     bonuses: Collection of magical/situational AC bonuses
   
   Returns:
     Total armor class as integer"
  [base-ac dex-mod bonuses]
  (+ base-ac dex-mod (apply + bonuses)))
```

#### re-frame Conventions
```clojure
;; Use namespaced event keywords
(dispatch [:character/update-name "New Name"])
(dispatch [:spell/add-to-spellbook spell-id])

;; Keep event handlers pure
(reg-event-db
  :character/level-up
  (fn [db [_ character-id]]
    (update-in db [:characters character-id :level] inc)))

;; Use descriptive subscription names
(reg-sub
  :character/spell-slots-remaining
  (fn [db [_ character-id]]
    (calculate-remaining-spell-slots 
      (get-in db [:characters character-id]))))
```

#### HTML/CSS Guidelines
```clojure
;; Use semantic Hiccup markup
[:section.character-sheet
 [:header.character-header
  [:h1.character-name (:name character)]
  [:p.character-details 
   "Level " (:level character) " " (:class character)]]
 [:main.character-stats
  ;; Character stats here
  ]]

;; Use CSS classes for styling, not inline styles
[:button.btn.btn-primary {:on-click handle-save} "Save Character"]
;; Instead of:
[:button {:style {:background "blue" :color "white"}} "Save"]
```

### Testing Requirements

#### Writing Tests
Every contribution should include appropriate tests:

**For Pure Functions:**
```clojure
(deftest ability-modifier-test
  (testing "ability modifier calculation"
    (are [score expected] (= expected (ability-modifier score))
      1  -5
      8  -1
      10  0
      16  3
      20  5)))
```

**For re-frame Events:**
```clojure
(deftest character-update-events
  (testing "character name update"
    (let [initial-db {:current-character {:name "Old Name"}}
          result (events/update-character-name 
                   initial-db 
                   [:character/update-name "New Name"])]
      (is (= "New Name" 
             (get-in (:db result) [:current-character :name]))))))
```

**For UI Components:**
```clojure
(deftest character-display-test
  (testing "character display component"
    (let [character {:name "Test" :level 5 :class "Fighter"}
          component (character-summary character)]
      (is (string/includes? (str component) "Test"))
      (is (string/includes? (str component) "Level 5")))))
```

#### Running Tests
```bash
# Run all tests before submitting
lein test

# Run specific test file
lein test orcpub.character-test

# Run tests continuously during development
lein test-refresh
```

### Documentation Standards

#### Code Documentation
- Document all public functions with docstrings
- Include parameter descriptions and return values
- Add examples for complex functions
- Explain any non-obvious business logic

#### User Documentation
- Use clear, beginner-friendly language
- Include examples and code samples
- Provide links to external resources
- Test all instructions on a fresh setup

## 🔍 Pull Request Process

### Before Submitting

**Quality Checklist:**
- [ ] **Tests pass**: `lein test` succeeds
- [ ] **Code formatted**: `lein cljfmt check` passes
- [ ] **Manual testing**: Changes work as expected in browser
- [ ] **Documentation**: Updated any relevant docs
- [ ] **No console errors**: Check browser developer tools

**Performance Checklist:**
- [ ] No obvious performance regressions
- [ ] Database queries are efficient
- [ ] UI remains responsive

### Pull Request Template

Use this template when creating your pull request:

```markdown
## Description
Brief description of what this PR does and why.

Fixes #(issue number)

## Type of Change
- [ ] Bug fix (non-breaking change that fixes an issue)
- [ ] New feature (non-breaking change that adds functionality)
- [ ] Breaking change (fix or feature that changes existing behavior)
- [ ] Documentation update

## Testing
Describe how you tested your changes:

- [ ] Unit tests added/updated
- [ ] Manual testing completed
- [ ] Tested in multiple browsers
- [ ] Tested with different character builds

## Screenshots
Include screenshots for UI changes.

## Checklist
- [ ] Tests pass locally
- [ ] Code follows project style guidelines
- [ ] Self-review of code completed
- [ ] Documentation updated
- [ ] No new warnings or errors
```

### Review Process

1. **Automated Checks**: GitHub Actions will run tests and checks
2. **Code Review**: Maintainers will review your code
3. **Feedback**: You may receive requests for changes
4. **Approval**: Once approved, your PR will be merged

**Responding to Feedback:**
- Be open to suggestions and questions
- Make requested changes promptly
- Ask for clarification if feedback is unclear
- Update your branch with any changes

## 🏗️ Types of Contributions

### Bug Fixes

**Finding Bugs:**
1. Check existing issues for known bugs
2. Test edge cases in the application
3. Try unusual character combinations
4. Test on different browsers/devices

**Fixing Bugs:**
1. Write a test that reproduces the bug
2. Fix the minimal code needed to make test pass
3. Verify fix doesn't break existing functionality
4. Update documentation if needed

**Example Bug Fix:**
```clojure
;; Bug: Spell DC calculation incorrect for sorcerers
;; Test first:
(deftest spell-dc-calculation-test
  (testing "sorcerer spell save DC"
    (let [sorcerer {:class :sorcerer 
                    :level 5
                    :abilities {:charisma 16}}
          expected-dc (+ 8 3 3)] ; 8 + proficiency + cha modifier
      (is (= expected-dc (calculate-spell-save-dc sorcerer))))))

;; Then fix:
(defn calculate-spell-save-dc [character]
  (let [casting-ability (get-casting-ability (:class character))
        ability-mod (ability-modifier 
                     (get-in character [:abilities casting-ability]))
        prof-bonus (proficiency-bonus (:level character))]
    (+ 8 prof-bonus ability-mod)))
```

### Adding New Features

**Feature Planning:**
1. Discuss the feature in an issue first
2. Consider how it fits with existing architecture
3. Plan the user interface and experience
4. Consider edge cases and error handling

**Implementation Process:**
1. Start with the data model/backend logic
2. Add necessary database schema changes
3. Implement UI components
4. Add comprehensive tests
5. Update documentation

### Adding D&D Content

**Content Sources:**
- **Official D&D content only** - We cannot accept copyrighted material
- **System Reference Document (SRD)** content is acceptable
- **Open Game License (OGL)** content may be acceptable

**Content Structure:**
```clojure
;; Example: Adding a new race
{:name "Dragonborn"
 :key :dragonborn
 :size :medium
 :speed 30
 :languages #{:common :draconic}
 :modifiers [(modifier ?str-bonus (+ ?str-bonus 2))
             (modifier ?cha-bonus (+ ?cha-bonus 1))
             (modifier ?damage-resistance 
                      (conj ?damage-resistance damage-type))]
 :selections [{:key :draconic-ancestry
               :min 1
               :max 1
               :options draconic-ancestry-options}]}
```

### Documentation Improvements

**Types of Documentation:**
- **Developer docs**: Setup guides, architecture explanations
- **User docs**: How to use the application
- **API docs**: Function documentation and examples
- **Troubleshooting**: Common issues and solutions

**Documentation Standards:**
- Write for your audience (beginners vs experts)
- Include working code examples
- Test all instructions on a clean setup
- Keep language clear and concise

## 🤝 Community Guidelines

### Code of Conduct

We are committed to providing a welcoming and inclusive environment:

- **Be respectful** of different viewpoints and experiences
- **Be collaborative** and help others learn
- **Focus on the code**, not personal characteristics
- **Give constructive feedback** with specific suggestions
- **Ask questions** when you need help

### Getting Help

**Where to Ask Questions:**
- **GitHub Discussions**: General questions and discussions
- **GitHub Issues**: Bug reports and feature requests
- **Pull Request Comments**: Questions about specific code

**How to Ask Good Questions:**
1. Search existing issues/discussions first
2. Provide context about what you're trying to do
3. Include error messages and code samples
4. Describe what you've already tried

**Response Times:**
- Issues and PRs are typically reviewed within a few days
- Complex changes may take longer to review
- Be patient - maintainers are volunteers

## 🎓 Learning Resources

### Clojure Learning Path
1. **Basics**: [Clojure for the Brave and True](https://www.braveclojure.com/)
2. **Practice**: [4clojure](https://4clojure.oxal.org/) exercises
3. **Reference**: [ClojureDocs](https://clojuredocs.org/)
4. **Community**: [Clojure Slack](http://clojurians.net/)

### re-frame Learning Path
1. **Tutorial**: [Official re-frame tutorial](https://day8.github.io/re-frame/)
2. **Examples**: Study the orcpub codebase
3. **Debugging**: Use re-frame-10x dev tools
4. **Patterns**: Learn subscription composition and event chains

### D&D 5e Resources
1. **System Reference Document**: [Official SRD](https://dnd.wizards.com/resources/systems-reference-document)
2. **Rules Reference**: [Basic Rules PDF](https://dnd.wizards.com/products/tabletop/players-basic-rules)
3. **Community**: [r/DMAcademy](https://reddit.com/r/DMAcademy), [r/dndnext](https://reddit.com/r/dndnext)

## 📊 Project Roadmap

### Current Priorities
1. **Stability**: Fix existing bugs and improve test coverage
2. **Performance**: Optimize slow operations and large character sheets
3. **Usability**: Improve user interface and experience
4. **Mobile**: Better mobile browser support

### Future Goals
1. **Additional Game Systems**: Support for other RPG systems
2. **Advanced Features**: Campaign management, party tracking
3. **Integration**: APIs for third-party tool integration
4. **Collaboration**: Real-time character sharing and editing

## 🏆 Recognition

Contributors are recognized in several ways:

- **Commit Attribution**: Your contributions are permanently recorded in Git history
- **Release Notes**: Major contributions are mentioned in release notes
- **Contributors List**: Regular contributors may be added to a contributors file
- **Mentorship**: Experienced contributors can mentor newcomers

---

**Ready to Contribute?**

1. Set up your [development environment](getting-started.md)
2. Find a [good first issue](https://github.com/codeGlaze/orcpub/labels/good%20first%20issue)
3. Follow the [development workflow](development-workflow.md)
4. Submit your first pull request!

**Questions?** Open an issue or start a discussion - we're here to help!