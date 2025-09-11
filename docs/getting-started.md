# Getting Started

This guide will walk you through setting up your development environment for Dungeon Master's Vault, with detailed instructions for developers new to the Clojure ecosystem.

## Prerequisites

### System Requirements

- **Operating System**: Windows 10+, macOS 10.14+, or Linux
- **Memory**: 4GB RAM minimum, 8GB recommended
- **Disk Space**: 2GB for development tools and dependencies
- **Network**: Internet connection for downloading dependencies

### Required Tools

#### 1. Java Development Kit (JDK)

Clojure runs on the Java Virtual Machine (JVM), so you need Java installed.

**For macOS:**
```bash
# Using Homebrew (recommended)
brew install openjdk@11

# Or download from Oracle/OpenJDK websites
```

**For Windows:**
```bash
# Using Chocolatey (recommended)
choco install openjdk11

# Or download from https://adoptium.net/
```

**For Linux (Ubuntu/Debian):**
```bash
sudo apt update
sudo apt install openjdk-11-jdk
```

**Verify Installation:**
```bash
java -version
# Should show Java 11 or higher
```

#### 2. Git

Required for version control and downloading the project.

**Installation:**
- **Windows**: Download from [git-scm.com](https://git-scm.com/download/win)
- **macOS**: `brew install git` or use Xcode Command Line Tools
- **Linux**: `sudo apt install git` (Ubuntu/Debian)

**Verify Installation:**
```bash
git --version
```

#### 3. Leiningen (Clojure Build Tool)

Leiningen is the standard build tool for Clojure projects.

**For macOS/Linux:**
```bash
# Download the lein script
curl -O https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein

# Make it executable
chmod +x lein

# Move to your PATH
sudo mv lein /usr/local/bin/

# Test installation (this will download Leiningen)
lein version
```

**For Windows:**
```bash
# Using Chocolatey
choco install lein

# Or download lein.bat from https://leiningen.org/
```

**What is Leiningen?**
Leiningen handles:
- Dependency management (like npm for Node.js)
- Compilation of Clojure/ClojureScript code
- Running development servers and REPLs
- Testing and packaging

#### 4. Node.js (Optional, for some development tools)

Some ClojureScript development tools use Node.js.

**Installation:**
- Download from [nodejs.org](https://nodejs.org/) (LTS version)
- Or use a package manager: `brew install node` (macOS), `choco install nodejs` (Windows)

#### 5. Code Editor/IDE

Choose one based on your preference:

**For Beginners:**
- **VS Code** with [Calva extension](https://marketplace.visualstudio.com/items?itemName=betterthantomorrow.calva)
  - Free, easy to set up
  - Good Clojure support out of the box
  - Integrated REPL

**For Advanced Users:**
- **IntelliJ IDEA** with [Cursive plugin](https://cursive-ide.com/)
  - Professional IDE experience
  - Excellent debugging and refactoring tools
  
- **Emacs** with [CIDER](https://cider.mx/)
  - Traditional Lisp development environment
  - Very powerful but steeper learning curve

- **Vim** with [vim-fireplace](https://github.com/tpope/vim-fireplace)
  - Lightweight, terminal-based

## Project Setup

### 1. Fork and Clone the Repository

**Fork the Project:**
1. Go to [https://github.com/codeGlaze/orcpub](https://github.com/codeGlaze/orcpub)
2. Click the "Fork" button to create your own copy

**Clone Your Fork:**
```bash
# Replace 'yourusername' with your GitHub username
git clone https://github.com/yourusername/orcpub.git
cd orcpub

# Add the original repository as upstream
git remote add upstream https://github.com/codeGlaze/orcpub.git
```

### 2. Install Dependencies

The project uses Leiningen to manage dependencies automatically:

```bash
# This will download all required Clojure dependencies
lein deps

# This may take several minutes on the first run
```

**What happens during `lein deps`:**
- Downloads Clojure and ClojureScript compilers
- Installs web framework dependencies (Pedestal, Ring)
- Downloads UI libraries (Reagent, re-frame)
- Installs database dependencies (Datomic)

### 3. Set Up Datomic Database

Datomic is the database used to store character data.

**Download Datomic Free:**
```bash
# Create a lib directory for Datomic
mkdir -p lib

# Download Datomic Free (Linux/macOS)
cd lib
curl -O https://github.com/Orcpub/orcpub/raw/develop/lib/datomic-free-0.9.5703.tar.gz
tar -xzf datomic-free-0.9.5703.tar.gz
cd ..
```

**For Windows:**
Download the file manually and extract to `lib/datomic-free-0.9.5703/`

**Start Datomic Transactor:**

*Linux/macOS:*
```bash
cd lib/datomic-free-0.9.5703
bin/transactor config/samples/free-transactor-template.properties
```

*Windows:*
```cmd
cd lib\datomic-free-0.9.5703
bin\transactor config\samples\free-transactor-template.properties
```

**Leave this running** - it's your database server.

## Development Environment Setup

### 1. Start the Development REPL

The REPL (Read-Eval-Print Loop) is your interactive development environment:

```bash
# From the project root directory
lein with-profile +start-server repl
```

**What is a REPL?**
- Interactive programming environment
- Allows you to test code immediately
- Modify running programs without restarting
- Essential for Clojure development

### 2. Initialize the Database

In your REPL, run these commands once:

```clojure
;; Initialize the database schema
(init-database)

;; Start the web server
(start-server)
```

### 3. Start the Frontend Development Server

Open a **new terminal** while keeping the REPL running:

```bash
# This starts the ClojureScript compiler and dev server
lein figwheel
```

**What is Figwheel?**
- Compiles ClojureScript to JavaScript
- Provides hot-reloading (changes appear instantly in browser)
- Creates a browser-connected REPL for interactive development

### 4. Access the Application

After both servers are running:
- Open your browser to [http://localhost:8890](http://localhost:8890)
- You should see the Dungeon Master's Vault interface

## Development Workflow

### Making Changes

1. **Backend Changes** (Clojure files in `src/clj/`):
   - Edit files in your editor
   - Save the file
   - In the REPL, reload the namespace: `(require '[your.namespace :as ns] :reload)`

2. **Frontend Changes** (ClojureScript files in `src/cljs/`):
   - Edit files in your editor
   - Save the file
   - Changes appear automatically in browser (hot-reloading)

### Testing Your Changes

```bash
# Run all tests
lein test

# Run specific test namespaces
lein test orcpub.entity-spec
```

### Code Formatting

```bash
# Check code formatting
lein cljfmt check

# Auto-format code
lein cljfmt fix
```

## Common Issues and Troubleshooting

### Issue: "Could not find artifact" errors

**Problem**: Dependency download failures
**Solution**: 
```bash
# Clear your local Maven cache and retry
rm -rf ~/.m2/repository
lein clean
lein deps
```

### Issue: "Port 8890 already in use"

**Problem**: Previous development server still running
**Solution**:
```bash
# Find and kill the process using port 8890
lsof -i :8890  # macOS/Linux
netstat -ano | findstr :8890  # Windows

# Or change the port in project.clj
```

### Issue: Browser shows "Connection refused"

**Problem**: Web server not started
**Solution**:
1. Make sure REPL is running
2. Run `(start-server)` in the REPL
3. Check that Datomic transactor is running

### Issue: Database errors

**Problem**: Datomic not accessible
**Solution**:
1. Ensure Datomic transactor is running
2. Run `(init-database)` in REPL if it's a fresh setup
3. Check database URL in configuration

## Next Steps

Now that your development environment is set up:

1. **Explore the Code**: Check out our [Source File Guide](source-guide.md)
2. **Learn the Technologies**: Read our [Technology Overview](technology-overview.md)
3. **Make Your First Change**: See our [Development Workflow](development-workflow.md)
4. **Contribute**: Review our [Contributing Guide](contributing.md)

## Learning Resources

### Clojure Learning
- [Clojure for the Brave and True](https://www.braveclojure.com/) - Beginner-friendly book
- [4clojure](https://4clojure.oxal.org/) - Interactive Clojure exercises
- [ClojureDocs](https://clojuredocs.org/) - Function documentation and examples

### ClojureScript & Web Development
- [ClojureScript Unraveled](https://funcool.github.io/clojurescript-unraveled/) - Comprehensive guide
- [Reagent Documentation](https://reagent-project.github.io/) - React in ClojureScript
- [re-frame Documentation](https://day8.github.io/re-frame/) - Application state management

---

**Need Help?** 
- Check our [Troubleshooting Guide](development-workflow.md#troubleshooting)
- Ask questions in [GitHub Issues](https://github.com/codeGlaze/orcpub/issues)
- Review existing documentation in the [docs](index.md) folder