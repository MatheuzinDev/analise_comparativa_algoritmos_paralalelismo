import static org.jocl.CL.CL_CONTEXT_PLATFORM;
import static org.jocl.CL.CL_DEVICE_TYPE_GPU;
import static org.jocl.CL.CL_MEM_COPY_HOST_PTR;
import static org.jocl.CL.CL_MEM_READ_ONLY;
import static org.jocl.CL.CL_MEM_WRITE_ONLY;
import static org.jocl.CL.CL_PROGRAM_BUILD_LOG;
import static org.jocl.CL.CL_SUCCESS;
import static org.jocl.CL.CL_TRUE;
import static org.jocl.CL.clBuildProgram;
import static org.jocl.CL.clCreateBuffer;
import static org.jocl.CL.clCreateCommandQueue;
import static org.jocl.CL.clCreateContext;
import static org.jocl.CL.clCreateKernel;
import static org.jocl.CL.clCreateProgramWithSource;
import static org.jocl.CL.clEnqueueNDRangeKernel;
import static org.jocl.CL.clEnqueueReadBuffer;
import static org.jocl.CL.clFinish;
import static org.jocl.CL.clGetDeviceIDs;
import static org.jocl.CL.clGetPlatformIDs;
import static org.jocl.CL.clGetProgramBuildInfo;
import static org.jocl.CL.clReleaseCommandQueue;
import static org.jocl.CL.clReleaseContext;
import static org.jocl.CL.clReleaseKernel;
import static org.jocl.CL.clReleaseMemObject;
import static org.jocl.CL.clReleaseProgram;
import static org.jocl.CL.clSetKernelArg;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jocl.CL;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_context_properties;
import org.jocl.cl_device_id;
import org.jocl.cl_kernel;
import org.jocl.cl_mem;
import org.jocl.cl_platform_id;
import org.jocl.cl_program;

public class ParallelGPUCounter implements WordCounter {
    private static final Path DEFAULT_KERNEL_PATH = Path.of("opencl", "word_count.cl");
    private static final String KERNEL_NAME = "count_words";

    private final Path kernelPath;

    public ParallelGPUCounter() {
        this(DEFAULT_KERNEL_PATH);
    }

    public ParallelGPUCounter(Path kernelPath) {
        this.kernelPath = kernelPath;
    }

    @Override
    public String name() {
        return "ParallelGPU";
    }

    @Override
    public long count(byte[] normalizedText, byte[] normalizedWord) throws Exception {
        validateInput(normalizedText, normalizedWord);

        if (normalizedText.length == 0 || normalizedWord.length > normalizedText.length) {
            return 0;
        }

        SelectedDevice selectedDevice = selectGpuDevice();
        String kernelSource = readKernelSource();
        OpenCLResources resources = null;
        cl_program program = null;
        cl_kernel kernel = null;
        cl_mem textBuffer = null;
        cl_mem wordBuffer = null;
        cl_mem matchesBuffer = null;

        int textLength = normalizedText.length;
        int wordLength = normalizedWord.length;
        int[] matches = new int[textLength];

        try {
            resources = createResources(selectedDevice);
            program = buildProgram(resources.context, selectedDevice.device, kernelSource);
            kernel = clCreateKernel(program, KERNEL_NAME, null);

            textBuffer = clCreateBuffer(
                    resources.context,
                    CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                    (long) Sizeof.cl_char * textLength,
                    Pointer.to(normalizedText),
                    null);

            wordBuffer = clCreateBuffer(
                    resources.context,
                    CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                    (long) Sizeof.cl_char * wordLength,
                    Pointer.to(normalizedWord),
                    null);

            matchesBuffer = clCreateBuffer(
                    resources.context,
                    CL_MEM_WRITE_ONLY,
                    (long) Sizeof.cl_int * textLength,
                    null,
                    null);

            clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(textBuffer));
            clSetKernelArg(kernel, 1, Sizeof.cl_int, Pointer.to(new int[] { textLength }));
            clSetKernelArg(kernel, 2, Sizeof.cl_mem, Pointer.to(wordBuffer));
            clSetKernelArg(kernel, 3, Sizeof.cl_int, Pointer.to(new int[] { wordLength }));
            clSetKernelArg(kernel, 4, Sizeof.cl_mem, Pointer.to(matchesBuffer));

            long[] globalWorkSize = new long[] { textLength };
            clEnqueueNDRangeKernel(resources.commandQueue, kernel, 1, null, globalWorkSize, null, 0, null, null);
            clFinish(resources.commandQueue);

            clEnqueueReadBuffer(
                    resources.commandQueue,
                    matchesBuffer,
                    CL_TRUE,
                    0,
                    (long) Sizeof.cl_int * textLength,
                    Pointer.to(matches),
                    0,
                    null,
                    null);

            return sumMatches(matches);
        } finally {
            releaseMemObject(matchesBuffer);
            releaseMemObject(wordBuffer);
            releaseMemObject(textBuffer);
            releaseKernel(kernel);
            releaseProgram(program);
            releaseResources(resources);
        }
    }

    private String readKernelSource() throws IOException {
        if (!Files.exists(kernelPath)) {
            throw new IOException("Kernel OpenCL nao encontrado em: " + kernelPath);
        }

        return Files.readString(kernelPath, StandardCharsets.UTF_8);
    }

    private static OpenCLResources createResources(SelectedDevice selectedDevice) {
        cl_context_properties contextProperties = new cl_context_properties();
        contextProperties.addProperty(CL_CONTEXT_PLATFORM, selectedDevice.platform);

        cl_context context = clCreateContext(
                contextProperties,
                1,
                new cl_device_id[] { selectedDevice.device },
                null,
                null,
                null);

        cl_command_queue commandQueue = clCreateCommandQueue(context, selectedDevice.device, 0, null);
        return new OpenCLResources(context, commandQueue);
    }

    private static cl_program buildProgram(cl_context context, cl_device_id device, String kernelSource) {
        cl_program program = clCreateProgramWithSource(context, 1, new String[] { kernelSource }, null, null);

        CL.setExceptionsEnabled(false);
        int buildStatus = clBuildProgram(program, 0, null, null, null, null);
        CL.setExceptionsEnabled(true);

        if (buildStatus != CL_SUCCESS) {
            String buildLog = getBuildLog(program, device);
            clReleaseProgram(program);
            throw new IllegalStateException("Falha ao compilar kernel OpenCL. Log: " + buildLog);
        }

        return program;
    }

    private static String getBuildLog(cl_program program, cl_device_id device) {
        long[] logSize = new long[1];
        clGetProgramBuildInfo(program, device, CL_PROGRAM_BUILD_LOG, 0, null, logSize);

        byte[] logData = new byte[(int) logSize[0]];
        clGetProgramBuildInfo(program, device, CL_PROGRAM_BUILD_LOG, logData.length, Pointer.to(logData), null);

        return new String(logData, StandardCharsets.UTF_8).trim();
    }

    private static SelectedDevice selectGpuDevice() {
        CL.setExceptionsEnabled(false);
        try {
            int[] platformCount = new int[1];
            int platformStatus = clGetPlatformIDs(0, null, platformCount);
            if (platformStatus != CL_SUCCESS || platformCount[0] == 0) {
                throw new IllegalStateException("Nenhuma plataforma OpenCL foi encontrada.");
            }

            cl_platform_id[] platforms = new cl_platform_id[platformCount[0]];
            clGetPlatformIDs(platforms.length, platforms, null);

            for (cl_platform_id platform : platforms) {
                int[] deviceCount = new int[1];
                int deviceStatus = clGetDeviceIDs(platform, CL_DEVICE_TYPE_GPU, 0, null, deviceCount);
                if (deviceStatus != CL_SUCCESS || deviceCount[0] == 0) {
                    continue;
                }

                cl_device_id[] devices = new cl_device_id[deviceCount[0]];
                clGetDeviceIDs(platform, CL_DEVICE_TYPE_GPU, devices.length, devices, null);
                return new SelectedDevice(platform, devices[0]);
            }

            throw new IllegalStateException("Nenhuma GPU OpenCL foi encontrada.");
        } finally {
            CL.setExceptionsEnabled(true);
        }
    }

    private static long sumMatches(int[] matches) {
        long total = 0;
        for (int match : matches) {
            total += match;
        }

        return total;
    }

    private static void validateInput(byte[] normalizedText, byte[] normalizedWord) {
        if (normalizedText == null) {
            throw new IllegalArgumentException("O texto normalizado nao pode ser nulo.");
        }

        if (normalizedWord == null || normalizedWord.length == 0) {
            throw new IllegalArgumentException("A palavra normalizada nao pode ser vazia.");
        }
    }

    private static void releaseMemObject(cl_mem memoryObject) {
        if (memoryObject != null) {
            clReleaseMemObject(memoryObject);
        }
    }

    private static void releaseKernel(cl_kernel kernel) {
        if (kernel != null) {
            clReleaseKernel(kernel);
        }
    }

    private static void releaseProgram(cl_program program) {
        if (program != null) {
            clReleaseProgram(program);
        }
    }

    private static void releaseResources(OpenCLResources resources) {
        if (resources == null) {
            return;
        }

        if (resources.commandQueue != null) {
            clReleaseCommandQueue(resources.commandQueue);
        }

        if (resources.context != null) {
            clReleaseContext(resources.context);
        }
    }

    private static final class SelectedDevice {
        private final cl_platform_id platform;
        private final cl_device_id device;

        private SelectedDevice(cl_platform_id platform, cl_device_id device) {
            this.platform = platform;
            this.device = device;
        }
    }

    private static final class OpenCLResources {
        private final cl_context context;
        private final cl_command_queue commandQueue;

        private OpenCLResources(cl_context context, cl_command_queue commandQueue) {
            this.context = context;
            this.commandQueue = commandQueue;
        }
    }
}
