// gcc -Wall -o zeromem zeromem.c && ./zeromem pid

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

void zeroMemory(char *start, char *end, FILE *pmem, FILE *out) {
    unsigned long long offset = strtoull(start, NULL, 16);
    uint bytes = strtoull(end, NULL, 16) - offset;

    fseeko(pmem, offset, SEEK_SET);
    char buf[1] = {0};
    uint count = 0;
    int success = 1;
    while (count < bytes && success) {
        success = fwrite(buf, 1, 1, pmem);
        count++;
    }
    if (count != bytes) { 
        fprintf(stderr, "%s-%s failed to clear %d bytes (actual count %d): %s\n",
            start, end, bytes, count, strerror(errno));
    } else {
        fprintf(out, "%s-%s cleared %d bytes\n", start, end, bytes);
    }
}

void processMap(char *matches[], void **args) {
    // Get args out of what's been passed to us
    FILE *pmem = (FILE *)args[0];
    FILE *out = (FILE *)args[1];
    //char *line = matches[0]; // line 
    char *start = matches[1]; // start
    char *end = matches[2]; // end 
    //char *readaccess = matches[3]; // r or -
    char *writeaccess = matches[4]; // w or -
    
    if (!strcmp(writeaccess, "w"))
        zeroMemory(start, end, pmem, out);
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
        char *matches[maxGroups];
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
        processParsedLine(matches, args);
        for (g = 0; g < maxGroups; g++) {
            free(matches[g]);
        }
        cursor += offset;
    }
}

void readAllPages(FILE *map, FILE *pmem, FILE *out) {
    char *line = NULL;
    size_t len;
    size_t read;
    char *regexString = "^([0-9A-Fa-f]+)-([0-9A-Fa-f]+) ([-r])([-w])";
    regex_t regexCompiled;

    if (regcomp(&regexCompiled, regexString, REG_EXTENDED)) {
        fprintf(stderr, "Could not compile regular expression.\n");
        return;
    };
    while ((read = getline(&line, &len, map)) != -1) {
        //printf("Retrieved line of length %zu :\n", read);
        //printf("%s", line);
        void *args[] = { pmem, out };
        parseLine(line, &regexCompiled, 5, &processMap, args);
    }
    if (line)
        free(line);
    regfree(&regexCompiled);
}

int readProcess(pid_t pid, FILE *out, int shallTerminate) {
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
    mem_fd = fopen(mem_file_name, "r+b");
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

    if (shallTerminate) { // shallTerminate is normal
        // Again, assuming we're root
        //ptrace(PTRACE_DETACH, pid, NULL, NULL);
        // But need to continue - since proc memory is zeroed, this
        // will likely result in the process seg faulting
        kill(pid, SIGCONT);
        // We'll terminate it too, but it's probably already done
        kill(pid, SIGTERM);
    }
    fclose(mem_fd);
    fclose(map_fd);
    return 0;
}
int main(int argc, char *argv[]) {
    pid_t pid;
    int pidarg = 1;
    int shallTerminate = 1;

    if (argc < 2) {
        printf("usage: zeromem [-s] pid\n");
        printf("\tuse -s to stop rather than terminate the process after wiping\n");
        return 1;
    }
    if (argc == 3 && !strcmp(argv[1],"-s")){
        shallTerminate = 0;
        pidarg = 2;
    }
    pid = atoi(argv[pidarg]);
    if (pid == 0) {
        fprintf(stderr, "invalid pid\n");
        return 1;
    }
    return readProcess(pid, stdout, shallTerminate);
}
