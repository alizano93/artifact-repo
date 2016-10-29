// gcc -Wall -o readm readmem.c && ./readm pid

#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <signal.h>
#include <regex.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/types.h>
#include <errno.h>

extern int errno;

// This limits us to 99999. /proc/sys/kernel/pid_max holds the actual max
#define PID_MAX_CHARS 5

void readMemory(char *start, char *end, FILE *pmem, FILE *out, char *line) {
    unsigned long long offset = strtoull(start, NULL, 16);
    uint bytes = strtoull(end, NULL, 16) - offset;
    //char buf[bytes]; // bytes can be large - use malloc to avoid overflow
    void* buf = malloc(bytes);
    fseeko(pmem, offset, SEEK_SET);

    int count = fread(buf, 1, bytes, pmem);
    if (count == bytes) { 
        fwrite(buf, 1, bytes, out);
    } else {
        if (strstr(line, "[vvar]") == NULL) {
            // vvar is shared kernel variables and it's a special
            // place. In many cases a failure to read this would be
            // expected
            fprintf(stderr, "failed to read %d bytes from memory location: %llx - %s\n",
                bytes, offset, strerror(errno));
        }
    }
    free(buf);
}

void processMap(char *matches[], void **args) {
    // Get args out of what's been passed to us
    FILE *pmem = (FILE *)args[0];
    FILE *out = (FILE *)args[1];
    //char *line = matches[0]; // line 
    char *start = matches[1]; // start
    char *end = matches[2]; // end 
    char *access = matches[3]; // r or -
    char *line = matches[4]; // parseLine will give us the whole line as last match
    if (!strcmp(access, "r"))
        readMemory(start, end, pmem, out, line);
}

void parseLine(char *line, regex_t *regexCompiled, size_t maxGroups,
                void (*processParsedLine)(char *matches[], void **args), void **args) {
    regmatch_t groupArray[maxGroups];
    unsigned int m;
    char * cursor;
    size_t maxMatches = 1;

    m = 0;
    cursor = line;
    for (m = 0; m < maxMatches; m ++) {
        if (regexec(regexCompiled, cursor, maxGroups, groupArray, 0))
            break;  // No more matches

        unsigned int g = 0;
        unsigned int offset = 0;
        char *matches[maxGroups + 1];
        for (g = 0; g < maxGroups; g++) {
            if (groupArray[g].rm_so == (size_t)-1)
                break;  // No more groups

            if (g == 0)
                offset = groupArray[g].rm_eo;

            char cursorCopy[strlen(cursor) + 1];
            strcpy(cursorCopy, cursor);
            cursorCopy[groupArray[g].rm_eo] = 0;

            matches[g] = (char *)malloc(strlen(cursorCopy + groupArray[g].rm_so));
            strcpy(matches[g], cursorCopy + groupArray[g].rm_so);
            // printf("Match %u, Group %u: [%2u-%2u]: %s\n",
            //      m, g, groupArray[g].rm_so, groupArray[g].rm_eo,
            //      cursorCopy + groupArray[g].rm_so);
        }
        matches[maxGroups] = line;
        processParsedLine(matches, args);
        for (g = 0; g < maxGroups; g++) { // we do not free the line passed to us
            free(matches[g]);
        }
        cursor += offset;
    }
}

void readAllPages(FILE *map, FILE *pmem, FILE *out) {
    char *line = NULL;
    size_t len;
    size_t read;
    char *regexString = "([0-9A-Fa-f]+)-([0-9A-Fa-f]+) ([-r])";
    //char *regexString = "([0-9A-Fa-f]+)-([0-9A-Fa-f]+) ([-r])... [0-9]{8} [0-9]{2}:[0-9]{2} [0-9]+ +(.*)";
    regex_t regexCompiled;

    if (regcomp(&regexCompiled, regexString, REG_EXTENDED)) {
        fprintf(stderr, "Could not compile regular expression.\n");
        return;
    };
    while ((read = getline(&line, &len, map)) != -1) {
        //printf("Retrieved line of length %zu :\n", read);
        //printf("%s", line);
        void *args[] = { pmem, out };
        parseLine(line, &regexCompiled, 4, &processMap, args);
    }
    if (line)
        free(line);
    regfree(&regexCompiled);
}

int readProcess(pid_t pid, FILE *out) {
    char map_file_name[12 + PID_MAX_CHARS];
    FILE *map_fd;

    char mem_file_name[11 + PID_MAX_CHARS];
    FILE *mem_fd;
    snprintf(map_file_name, 12 + PID_MAX_CHARS, "/proc/%d/maps", pid);
    map_fd = fopen(map_file_name, "r");
    if (map_fd == NULL) {
        fprintf(stderr, "fatal: could not open %s. Are you root?\n", map_file_name);
        return 1;
    }
    snprintf(mem_file_name, 11 + PID_MAX_CHARS, "/proc/%d/mem", pid);
    mem_fd = fopen(mem_file_name, "rb");
    if (mem_fd == NULL) {
        fprintf(stderr, "fatal: could not open %s. Are you root?\n", mem_file_name);
        return 1;
    }
    // We're going to assume we're root, so ptrace not necessary
    // ptrace(PTRACE_ATTACH, pid, NULL, NULL);

    // But we do need to send a stop signal
    kill(pid, SIGSTOP);
    // waitpid(pid, NULL, 0); // Wait necessary as ptrace is async
    readAllPages(map_fd, mem_fd, out);

    // Again, assuming we're root
    //ptrace(PTRACE_DETACH, pid, NULL, NULL);
    // But need to continue
    kill(pid, SIGCONT);
    fclose(mem_fd);
    fclose(map_fd);
    return 0;
}
int main(int argc, char *argv[]) {
    pid_t pid;

    if (argc < 2) {
        printf("usage: readmem pid\n");
        return 1;
    }
    pid = atoi(argv[1]);
    if (pid == 0) {
        fprintf(stderr, "invalid pid\n");
        return 1;
    }
    return readProcess(pid, stdout);
}
