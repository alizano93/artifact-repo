#include <iostream>
#include <fstream>
#include <unistd.h>
using namespace std;

int main (int argc, char** argv){
 
	if(argc < 2){
		cout<<"one argument is required"<<endl;
		return 0;
	}
   	
   	string path = argv[1];
   	cout<<"ramdisk path = "<<path<<endl;
	
	ofstream myFile;
	string name = path+"/example.out";
	myFile.open(name.c_str());
	myFile << "Writing some data to file.\n";
	myFile.close();
	
    while(1){}
   	return 0;
}