use warnings;
use strict;
use IO::File;

use Getopt::Long;
my $file = "/tmp/default_mockvep_writeTofile.txt";
my $batchSize = 2;
my $result = GetOptions (
        "o=s" => \$file, # -o string
        "b=i" => \$batchSize # -b integer
        );

my @buffer = ();
my $fileHandle = new IO::File;
$fileHandle->open(">> $file");

my $line;
while ($line = <STDIN>) {
    chomp ($line);
    push (@buffer, "$line annotated\n");
    my $bufferSize = scalar (@buffer);
    if ($bufferSize == $batchSize) {
        foreach my $bufferLine (@buffer) {
            print $fileHandle $bufferLine;
        }
        @buffer = ();
        $fileHandle->flush();
    }
}

foreach my $bufferLine (@buffer) {
    print $fileHandle $bufferLine;
}

print $fileHandle "extra line as if some variant had two annotations\n";
$fileHandle->close();
