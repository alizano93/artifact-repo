#include<stdio.h>
#include<cstdlib>
#include<iostream>
#include<string.h>
#include<fstream>
#include<list>
#include<dirent.h>

using namespace std;

void listFile(string path, const char * arg);
list<string> file_list;
int main(int argc, char** argv){
  const char * arg = argv[1];
  string path = argv[2];
    listFile("",arg);

    ofstream myOut;
	string fullPath = path+"/test.1B";
    myOut.open (fullPath.c_str(), ios::ate);

    for (list<string>::iterator it=file_list.begin(); it != file_list.end(); ++it){
      //  cout <<"++++"<<*it << "\n";
        string line;
		string readPath = *it;
        ifstream myfile;
		myfile.open(readPath.c_str(), ios::in);
        if (myfile.is_open())
        {
          while ( getline (myfile,line) )
          {
        //    cout<< line << "\n";
            myOut << line << '\n';
          }
          myfile.close();
        }

    }
    myOut.close();
    return 0;
}

void listFile(string path, const char * arg){
        DIR *pDIR;
        struct dirent *entry;
        if( (pDIR=opendir(arg))){
                while((entry = readdir(pDIR))){
                        if( strcmp(entry->d_name, ".") != 0 && strcmp(entry->d_name, "..") != 0 && strcmp(entry->d_name, ".DS_Store") != 0){
                          if(entry->d_type == DT_REG){
                              path=arg;
                              file_list.insert(file_list.end(), path+"/"+entry->d_name);
                          }
                          else{
                           if(entry->d_type == DT_DIR){
                              path=arg;
                              string newarg = path+"/"+entry->d_name;
                              listFile(path, newarg.c_str());
                            }
                          }
                        }
                }
                closedir(pDIR);
        }
}
